package top.zxylearn.chatserver.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.zxylearn.chatserver.dto.wallet.CreateRedPacketRequest;
import top.zxylearn.chatserver.entity.Account;
import top.zxylearn.chatserver.entity.AccountTransaction;
import top.zxylearn.chatserver.entity.Friend;
import top.zxylearn.chatserver.entity.Group;
import top.zxylearn.chatserver.entity.GroupMember;
import top.zxylearn.chatserver.entity.Notification;
import top.zxylearn.chatserver.entity.RedPacket;
import top.zxylearn.chatserver.entity.RedPacketReceive;
import top.zxylearn.chatserver.exception.BusinessException;
import top.zxylearn.chatserver.mapper.AccountMapper;
import top.zxylearn.chatserver.mapper.AccountTransactionMapper;
import top.zxylearn.chatserver.mapper.FriendMapper;
import top.zxylearn.chatserver.mapper.GroupMapper;
import top.zxylearn.chatserver.mapper.GroupMemberMapper;
import top.zxylearn.chatserver.mapper.NotificationMapper;
import top.zxylearn.chatserver.mapper.RedPacketMapper;
import top.zxylearn.chatserver.mapper.RedPacketReceiveMapper;
import top.zxylearn.chatserver.vo.AccountResponse;
import top.zxylearn.chatserver.vo.RedPacketClaimResponse;
import top.zxylearn.chatserver.vo.RedPacketResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WalletService {

    private static final BigDecimal MIN_UNIT = new BigDecimal("0.01");
    private static final BigDecimal MAX_TRANSACTION = new BigDecimal("100000.00");
    private final AccountMapper accountMapper;
    private final AccountTransactionMapper transactionMapper;
    private final RedPacketMapper redPacketMapper;
    private final RedPacketReceiveMapper receiveMapper;
    private final NotificationMapper notificationMapper;
    private final FriendMapper friendMapper;
    private final GroupMapper groupMapper;
    private final GroupMemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    public WalletService(
            AccountMapper accountMapper,
            AccountTransactionMapper transactionMapper,
            RedPacketMapper redPacketMapper,
            RedPacketReceiveMapper receiveMapper,
            NotificationMapper notificationMapper,
            FriendMapper friendMapper,
            GroupMapper groupMapper,
            GroupMemberMapper memberMapper,
            PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.transactionMapper = transactionMapper;
        this.redPacketMapper = redPacketMapper;
        this.receiveMapper = receiveMapper;
        this.notificationMapper = notificationMapper;
        this.friendMapper = friendMapper;
        this.groupMapper = groupMapper;
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public AccountResponse account(long userId) {
        return AccountResponse.from(requireAccount(userId));
    }

    @Transactional
    public AccountResponse setPayPassword(long userId, String oldPassword, String newPassword) {
        Account account = requireAccountForUpdate(userId);
        if (account.getPayPasswordHash() != null
                && (oldPassword == null || !passwordEncoder.matches(oldPassword, account.getPayPasswordHash()))) {
            throw new BusinessException("PAY_PASSWORD_INVALID", "旧支付密码不正确", HttpStatus.UNAUTHORIZED);
        }
        account.setPayPasswordHash(passwordEncoder.encode(newPassword));
        account.setUpdatedTime(LocalDateTime.now());
        accountMapper.updateById(account);
        return AccountResponse.from(account);
    }

    @Transactional
    public RedPacketResponse createRedPacket(long senderId, CreateRedPacketRequest request) {
        long targetId = parseId(request.targetId(), "INVALID_TARGET_ID", "红包目标ID不正确");
        validateChatAndCount(senderId, request.chatType(), targetId, request.totalCount());
        BigDecimal amount = parseAmount(request.totalAmount());
        if (amount.compareTo(MIN_UNIT.multiply(BigDecimal.valueOf(request.totalCount()))) < 0) {
            throw new BusinessException("RED_PACKET_AMOUNT_TOO_SMALL", "红包金额不足以保证每份至少0.01元", HttpStatus.BAD_REQUEST);
        }
        Account sender = requireAccountForUpdate(senderId);
        verifyPayment(sender, request.payPassword(), amount);
        LocalDateTime now = LocalDateTime.now();
        sender.setBalance(sender.getBalance().subtract(amount));
        sender.setUpdatedTime(now);
        accountMapper.updateById(sender);
        RedPacket packet = new RedPacket();
        packet.setSenderId(senderId);
        packet.setChatType(request.chatType());
        packet.setTargetId(targetId);
        packet.setPacketType(request.packetType());
        packet.setTotalAmount(amount);
        packet.setTotalCount(request.totalCount());
        packet.setRemainAmount(amount);
        packet.setRemainCount(request.totalCount());
        packet.setMessage(normalizeMessage(request.message()));
        packet.setStatus(0);
        packet.setExpireTime(now.plusHours(24));
        packet.setCreatedTime(now);
        packet.setUpdatedTime(now);
        redPacketMapper.insert(packet);
        transaction(null, senderId, null, 1, amount, packet.getId(), now);
        return RedPacketResponse.from(packet);
    }

    @Transactional
    public RedPacketClaimResponse claim(long userId, long packetId) {
        RedPacket packet = redPacketMapper.selectByIdForUpdate(packetId);
        if (packet == null) throw new BusinessException("RED_PACKET_NOT_FOUND", "红包不存在", HttpStatus.NOT_FOUND);
        validatePacketAccess(userId, packet, true);
        RedPacketReceive existing = receiveMapper.selectOne(new LambdaQueryWrapper<RedPacketReceive>()
                .eq(RedPacketReceive::getRedPacketId, packetId)
                .eq(RedPacketReceive::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            return new RedPacketClaimResponse(RedPacketResponse.from(packet), existing.getAmount().toPlainString(), true, false);
        }
        LocalDateTime now = LocalDateTime.now();
        if (packet.getStatus() == 2 || !packet.getExpireTime().isAfter(now)) {
            expireLockedPacket(packet, now);
            return new RedPacketClaimResponse(RedPacketResponse.from(packet), null, false, true);
        }
        if (packet.getStatus() != 0 || packet.getRemainCount() <= 0) {
            throw new BusinessException("RED_PACKET_EMPTY", "红包已经领完", HttpStatus.CONFLICT);
        }
        BigDecimal amount = calculateClaimAmount(packet);
        Account receiver = requireAccountForUpdate(userId);
        receiver.setBalance(receiver.getBalance().add(amount));
        receiver.setUpdatedTime(now);
        accountMapper.updateById(receiver);
        packet.setRemainAmount(packet.getRemainAmount().subtract(amount));
        packet.setRemainCount(packet.getRemainCount() - 1);
        if (packet.getRemainCount() == 0) packet.setStatus(1);
        packet.setUpdatedTime(now);
        redPacketMapper.updateById(packet);
        RedPacketReceive receive = new RedPacketReceive();
        receive.setRedPacketId(packetId);
        receive.setUserId(userId);
        receive.setAmount(amount);
        receive.setCreatedTime(now);
        receiveMapper.insert(receive);
        transaction(null, packet.getSenderId(), userId, 2, amount, packetId, now);
        createNotification(packet.getSenderId(), 2, packetId, "红包被领取", "红包被领取 " + amount.toPlainString() + " 元", now);
        return new RedPacketClaimResponse(RedPacketResponse.from(packet), amount.toPlainString(), false, false);
    }

    public RedPacketResponse getRedPacket(long userId, long packetId) {
        RedPacket packet = redPacketMapper.selectById(packetId);
        if (packet == null) throw new BusinessException("RED_PACKET_NOT_FOUND", "红包不存在", HttpStatus.NOT_FOUND);
        validatePacketAccess(userId, packet, false);
        return RedPacketResponse.from(packet);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireRedPackets() {
        LocalDateTime now = LocalDateTime.now();
        List<RedPacket> expired = redPacketMapper.selectList(new LambdaQueryWrapper<RedPacket>()
                .eq(RedPacket::getStatus, 0)
                .le(RedPacket::getExpireTime, now)
                .last("LIMIT 100"));
        for (RedPacket item : expired) {
            RedPacket locked = redPacketMapper.selectByIdForUpdate(item.getId());
            if (locked != null && locked.getStatus() == 0 && !locked.getExpireTime().isAfter(now)) {
                expireLockedPacket(locked, now);
            }
        }
    }

    private void expireLockedPacket(RedPacket packet, LocalDateTime now) {
        if (packet.getStatus() == 2) return;
        if (packet.getRemainAmount().compareTo(BigDecimal.ZERO) > 0) {
            Account sender = requireAccountForUpdate(packet.getSenderId());
            BigDecimal refund = packet.getRemainAmount();
            sender.setBalance(sender.getBalance().add(refund));
            sender.setUpdatedTime(now);
            accountMapper.updateById(sender);
            transaction(null, null, packet.getSenderId(), 3, refund, packet.getId(), now);
            packet.setRemainAmount(BigDecimal.ZERO.setScale(2));
        }
        packet.setRemainCount(0);
        packet.setStatus(2);
        packet.setUpdatedTime(now);
        redPacketMapper.updateById(packet);
    }

    private BigDecimal calculateClaimAmount(RedPacket packet) {
        if (packet.getRemainCount() == 1) return packet.getRemainAmount();
        if (packet.getPacketType() == 0) {
            return packet.getTotalAmount()
                    .divide(BigDecimal.valueOf(packet.getTotalCount()), 2, RoundingMode.DOWN)
                    .max(MIN_UNIT);
        }
        long remainingCents = packet.getRemainAmount().movePointRight(2).longValueExact();
        long minReserved = packet.getRemainCount() - 1L;
        long maxByBalance = remainingCents - minReserved;
        long twiceAverage = Math.max(1, (remainingCents / packet.getRemainCount()) * 2);
        long upper = Math.max(1, Math.min(maxByBalance, twiceAverage));
        long cents = 1 + random.nextLong(upper);
        return BigDecimal.valueOf(cents, 2);
    }

    private void verifyPayment(Account account, String payPassword, BigDecimal amount) {
        if (account.getStatus() != 1) throw new BusinessException("ACCOUNT_DISABLED", "账户不可用", HttpStatus.CONFLICT);
        if (account.getPayPasswordHash() == null) {
            throw new BusinessException("PAY_PASSWORD_NOT_SET", "请先设置支付密码", HttpStatus.CONFLICT);
        }
        if (payPassword == null || !passwordEncoder.matches(payPassword, account.getPayPasswordHash())) {
            throw new BusinessException("PAY_PASSWORD_INVALID", "支付密码不正确", HttpStatus.UNAUTHORIZED);
        }
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("INSUFFICIENT_BALANCE", "账户余额不足", HttpStatus.CONFLICT);
        }
    }

    private void validateChatAndCount(long userId, int chatType, long targetId, int count) {
        if (chatType == 0) {
            if (count != 1) throw new BusinessException("DIRECT_RED_PACKET_COUNT", "单聊红包只能有1份", HttpStatus.BAD_REQUEST);
            requireFriends(userId, targetId);
            return;
        }
        Group group = groupMapper.selectById(targetId);
        if (group == null || group.getStatus() != 1) throw new BusinessException("GROUP_NOT_FOUND", "群聊不存在或已解散", HttpStatus.NOT_FOUND);
        if (memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, targetId).eq(GroupMember::getUserId, userId)) == 0) {
            throw new BusinessException("GROUP_NOT_MEMBER", "你已不在该群聊中", HttpStatus.FORBIDDEN);
        }
        long members = memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, targetId));
        if (count > members) throw new BusinessException("RED_PACKET_COUNT_TOO_LARGE", "红包份数不能超过群成员数", HttpStatus.BAD_REQUEST);
    }

    private void validatePacketAccess(long userId, RedPacket packet, boolean claiming) {
        if (packet.getChatType() == 0) {
            if (userId != packet.getTargetId() && userId != packet.getSenderId()) {
                throw new BusinessException("RED_PACKET_ACCESS_DENIED", "无权查看该红包", HttpStatus.FORBIDDEN);
            }
            if (claiming && userId == packet.getSenderId()) {
                throw new BusinessException("CANNOT_CLAIM_OWN_DIRECT_PACKET", "不能领取自己发送的单聊红包", HttpStatus.CONFLICT);
            }
            return;
        }
        if (memberMapper.selectCount(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, packet.getTargetId()).eq(GroupMember::getUserId, userId)) == 0) {
            throw new BusinessException("GROUP_NOT_MEMBER", "你已不在该群聊中", HttpStatus.FORBIDDEN);
        }
    }

    private void requireFriends(long first, long second) {
        long count = friendMapper.selectCount(new LambdaQueryWrapper<Friend>()
                .and(wrapper -> wrapper
                        .nested(pair -> pair.eq(Friend::getUserId, first).eq(Friend::getFriendId, second))
                        .or(pair -> pair.eq(Friend::getUserId, second).eq(Friend::getFriendId, first))));
        if (count != 2) throw new BusinessException("NOT_FRIEND", "当前已经不是好友关系", HttpStatus.CONFLICT);
    }

    private AccountTransaction transaction(
            String clientId, Long from, Long to, int type, BigDecimal amount, Long referenceId, LocalDateTime now) {
        AccountTransaction value = new AccountTransaction();
        value.setClientTransactionId(clientId);
        value.setFromUserId(from);
        value.setToUserId(to);
        value.setTransactionType(type);
        value.setAmount(amount);
        value.setReferenceId(referenceId);
        value.setStatus(1);
        value.setCreatedTime(now);
        value.setUpdatedTime(now);
        transactionMapper.insert(value);
        return value;
    }

    private void createNotification(long userId, int type, long referenceId, String title, String content, LocalDateTime now) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setNotificationType(type);
        notification.setReferenceId(referenceId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setCreatedTime(now);
        notification.setUpdatedTime(now);
        notificationMapper.insert(notification);
    }

    private Account requireAccount(long userId) {
        Account account = accountMapper.selectOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, userId).last("LIMIT 1"));
        if (account == null) throw new BusinessException("ACCOUNT_NOT_FOUND", "账户不存在", HttpStatus.NOT_FOUND);
        return account;
    }

    private Account requireAccountForUpdate(long userId) {
        Account account = accountMapper.selectByUserIdForUpdate(userId);
        if (account == null) throw new BusinessException("ACCOUNT_NOT_FOUND", "账户不存在", HttpStatus.NOT_FOUND);
        return account;
    }

    private BigDecimal parseAmount(String value) {
        BigDecimal amount;
        try { amount = new BigDecimal(value).setScale(2, RoundingMode.UNNECESSARY); }
        catch (Exception exception) { throw new BusinessException("INVALID_AMOUNT", "金额格式不正确", HttpStatus.BAD_REQUEST); }
        if (amount.compareTo(MIN_UNIT) < 0 || amount.compareTo(MAX_TRANSACTION) > 0) {
            throw new BusinessException("INVALID_AMOUNT", "金额必须在0.01到100000.00之间", HttpStatus.BAD_REQUEST);
        }
        return amount;
    }

    private long parseId(String value, String code, String message) {
        try { long id = Long.parseLong(value); if (id <= 0) throw new NumberFormatException(); return id; }
        catch (Exception exception) { throw new BusinessException(code, message, HttpStatus.BAD_REQUEST); }
    }

    private String normalizeMessage(String value) {
        if (value == null || value.isBlank()) return "恭喜发财，大吉大利";
        return value.trim();
    }
}

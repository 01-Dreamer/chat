当前项目是一个仿微信桌面即时通讯系统。

项目目录：

chat/
├── AGENTS.md
├── chatclient/
├── chatserver/
└── .git/

其中：

chatclient
已经完成 Electron + Vue 的前端原型，目前主要使用 Mock 数据。

chatserver
用于开发 Spring Boot 后端。

现在开始正式开发后端，并逐步将 chatclient 中的 Mock 数据替换为真实后端数，如果发现前端有需要修改的UI和结构，可以直接修改。

下面描述这个项目的主要模块：

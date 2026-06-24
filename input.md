
1. UI修改：键盘工具栏指定位置添加小号「童」按钮，底色与工具栏原生一致，设置可开关显示/隐藏；
2. 「童」按钮点击弹出仅覆盖键盘的问答浮窗，浮窗包含标题+关闭按钮、单行输入框+查询按钮、回复显示查询AI给与的答案内容，每条回复带插入按钮；浮窗全程不遮挡上层聊天消息；
3. 交互逻辑：输入文字→发起API→加载loading→返回回复；点击插入自动回填聊天框并关闭弹窗；点击×仅关闭弹窗；
4. API固定地址：http://124.221.113.165:8000/api/chat/ POST，入参openid/message/session_id，返回session_id/response/sources，处理401/429/500/800ms超时/无网络全部异常场景；

## 五、API接口强制规范（独立网络请求模块，仅问答调用）
### 5.1 请求基础信息
请求地址：`http://124.221.113.165:8000/api/chat/`
请求方式：POST
传输协议：HTTP 局域网内网接口（仅内网可访问）
请求头固定参数：
```json
Content-Type: application/json
```
完整curl调试示例：
```bash
curl -X POST http://124.221.113.165:8000/api/chat/ \
  -H "Content-Type: application/json" \
  -d '{
    "openid": "ime_user_001",
    "message": "维生素C有什么功效？",
    "session_id": null
  }'
```
### 5.2 接口入参说明
1. openid：设备/用户唯一标识，固定前缀`ime_user_`，区分不同客服设备；
2. message：清洗后的客户咨询问题文本；
3. session_id：对话会话标识，首次提问传`null`，多轮对话回传接口返回的session_id，承接上下文。
### 5.3 接口标准响应示例
```json
{
  "session_id": "abc123",
  "response": "根据知识库，这是关于营养师的回答...",
  "sources": [
    {
      "category": "产品",
      "title": "产品名称",
      "content": "相关内容...",
      "score": 0.85
    }
  ]
}
```

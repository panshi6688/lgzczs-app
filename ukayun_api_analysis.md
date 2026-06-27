# 优卡云供货商端 API 逆向分析

> 站点: http://supplier.ukayun.cn/
> 日期: 2026-06-15

---

## 一、响应解密（Response Decryption）

### 算法参数

| 参数 | 值 |
|------|-----|
| 算法 | AES-256-CBC |
| 模式 | CBC |
| 填充 | PKCS7 |
| Key（密钥） | `7aca3c37e3745f8768b0e559797d521f`（32字节 UTF-8，对应 AES-256） |
| IV（初始向量） | `MD5(key).substring(0, 16)` = `4364484b6ed4f6db` |
| 输出编码 | Base64 |
| 最终输出 | JSON |

### 解密流程（伪代码）

```
key  = "7aca3c37e3745f8768b0e559797d521f"
iv   = MD5(key).substring(0, 16)  // "4364484b6ed4f6db"

raw  = Base64Decode(ciphertext)
data = AES256Decrypt(raw, key, iv, mode=CBC, padding=PKCS7)
json = UTF8Decode(data)
```

### Python 代码示例

```python
import base64
import hashlib
from Crypto.Cipher import AES

KEY = b"7aca3c37e3745f8768b0e559797d521f"
IV = hashlib.md5(KEY).hexdigest()[:16].encode("utf-8")

def decrypt(ciphertext_b64: str) -> dict:
    raw = base64.b64decode(ciphertext_b64)
    cipher = AES.new(KEY, AES.MODE_CBC, iv=IV)
    data = cipher.decrypt(raw)
    # PKCS7 去填充
    pad_len = data[-1]
    data = data[:-pad_len]
    return json.loads(data.decode("utf-8"))
```

### JavaScript 代码示例

```javascript
const CryptoJS = require('crypto-js');

const KEY = CryptoJS.enc.Utf8.parse("7aca3c37e3745f8768b0e559797d521f");
const IV = CryptoJS.enc.Utf8.parse(
  CryptoJS.MD5("7aca3c37e3745f8768b0e559797d521f").toString().substring(0, 16)
);

function decrypt(ciphertext) {
  const bytes = CryptoJS.AES.decrypt(ciphertext, KEY, {
    iv: IV,
    mode: CryptoJS.mode.CBC,
    padding: CryptoJS.pad.Pkcs7
  });
  return JSON.parse(bytes.toString(CryptoJS.enc.Utf8));
}
```

---

## 二、请求签名（Request Signing）

### 签名参数

每个 API 请求需在 Header 中添加以下字段（salt 不在 Header 中发送，仅用于签名计算）：

| Header | 值 | 说明 |
|--------|-----|------|
| `Authorization` | `Bearer <admin_token>` | token 存于 `localStorage.admin_token` |
| `timestamp` | 服务器时间戳（秒） | 需从 `/spa/auth/timestamp` 获取 |
| `nonce` | 5位随机字符串 | 字符集: `abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890` |
| `sign` | SHA256(签名字符串) | 见下方签名算法 |
| `version` | `2` | 固定值 |

### 签名算法

```
1. 取请求参数（GET为params，POST为请求体JSON对象）
2. 如果是JSON字符串，先 JSON.parse 转对象
3. 用 filterData 过滤掉值为 null/undefined 的字段
4. 用 ksort 按 key 的字典序排序
5. 对每个 key=value 拼接（value为对象则JSON序列化后去掉转义引号）
6. 依次追加: nonce=xxx & salt=xxx & timestamp=xxx & version=2
7. 对上述字符串做 SHA256 → sign
```

**salt 计算公式：** `salt = MD5( str(timestamp)[-5:] + nonce )`

伪代码：
```
salt = MD5( (timestamp 字符串后5位) + nonce )
params = ksort(filterData(requestParams))
paramStr = join("&", params.map(k => k + "=" + v))
paramStr += "&nonce=" + nonce
paramStr += "&salt=" + salt
paramStr += "&timestamp=" + timestamp
paramStr += "&version=2"
sign = SHA256(paramStr)
```

### 时间戳处理

```javascript
// 前端代码逻辑：
// 1. 请求 /spa/auth/timestamp 获取服务器时间，计算与本地时间差值 SERVER_TIME_DIFF
//    GET http://supplier.ukayun.cn/spa/auth/timestamp → {"code":200,"data":{"time":1782486012}}
// 2. 每次签名时：timestamp = Math.floor((Date.now() + SERVER_TIME_DIFF) / 1000)
// 3. 若获取服务器时间失败，则用 Math.floor(Date.now() / 1000)
```

### Python 签名示例

```python
import hashlib
import random
import json

ALPHABET = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890'

def generate_nonce(length=5):
    return ''.join(random.choices(ALPHABET, k=length))

def build_sign(params: dict, timestamp: int, nonce: str) -> str:
    # salt = MD5(时间戳后5位 + nonce)
    salt = hashlib.md5((str(timestamp)[-5:] + nonce).encode()).hexdigest()
    
    # 过滤 null/undefined，按 key 排序
    filtered = {k: v for k, v in params.items() if v is not None}
    sorted_params = dict(sorted(filtered.items()))
    
    parts = []
    for k, v in sorted_params.items():
        if isinstance(v, (dict, list)):
            v = json.dumps(v, separators=(',', ':')).replace('\\"', '"')
        parts.append(f"{k}={v}")
    
    parts.append(f"nonce={nonce}")
    parts.append(f"salt={salt}")
    parts.append(f"timestamp={timestamp}")
    parts.append("version=2")
    
    raw = "&".join(parts)
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()
```

---

## 三、注意事项

1. **Key 是固定的**：`7aca3c37e3745f8768b0e559797d521f` 硬编码在前端 JS 中，所有接口共用同一个 key
2. **需要登录 Cookie**：请求需携带站点的 Cookie，否则返回 `500: 请刷新浏览器重新请求`
3. **Authorization Token**：登录后 token 存在 `localStorage.admin_token`，请求头需加 `Bearer <token>`
4. **顶象验证码**：前端接入了顶象（DingXiang）滑动验证码 `dxAppId=00d556272bc3035c4fba68384b181616`，登录等敏感操作可能需要
5. **服务端时间同步**：签名前需先请求 `/spa/auth/timestamp` 接口获取服务器时间，否则签名可能被拒
6. **请求路径前缀**：API 基础路径为 `http://supplier.ukayun.cn`，SPA 路径为 `/spa/`（前端 Vue Router）
7. **商品名称乱码**：解密后的 JSON 中 `goods_name` 字段可能出现乱码，这是终端编码问题，实际数据是正确的中文
8. **salt 计算更正**：salt = `MD5(str(timestamp)[-5:] + nonce)`，不是 `msgn5 + 时间戳后5位`
9. **nonce 字符集**：`abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890`（62字符），不是纯小写+数字

# 验收实测矩阵：全部真实 HTTP 调用 + DB 核验（2026-11-22）
$ErrorActionPreference = "Continue"
$base = "http://localhost:8082"
$mysql = "D:\MySQL\MySQL Server 8.0\bin\mysql.exe"
function SQL($q) { & $mysql "-h" "127.0.0.1" "-P" "3307" "-u" "root" "-p123456" "-D" "ai_customer_service" "-N" "-e" $q 2>$null }
function TryReq($tag, $script) { try { & $script } catch { $s = $_.Exception.Response.StatusCode.value__; "$tag HTTP=$s" } }

# 1 匿名 /me
TryReq "T01_anon_me" { $r = Invoke-WebRequest "$base/ai/me" -UseBasicParsing; "T01_anon_me HTTP=$($r.StatusCode)" }
# 2 login 缺参数
TryReq "T02_login_missing" { $r = Invoke-WebRequest "$base/ai/login?username=zhangsan" -Method POST -UseBasicParsing -SkipHttpErrorCheck; "T02_login_missing HTTP=$($r.StatusCode) BODY=$($r.Content)" }
# 3 错密码
$r = Invoke-WebRequest "$base/ai/login?username=zhangsan&password=wrong" -Method POST -UseBasicParsing -SkipHttpErrorCheck
"T03_wrong_pwd HTTP=$($r.StatusCode) BODY=$($r.Content)"
# 会话
$null = Invoke-WebRequest "$base/ai/login?username=zhangsan&password=123456" -Method POST -SessionVariable u -UseBasicParsing
$null = Invoke-WebRequest "$base/ai/login?username=lisi&password=123456" -Method POST -SessionVariable l -UseBasicParsing
$null = Invoke-WebRequest "$base/ai/login?username=admin&password=Admin@123" -Method POST -SessionVariable a -UseBasicParsing
# 4 chat 缺 message
TryReq "T04_chat_missing" { $r = Invoke-WebRequest "$base/ai/chat?conversationId=acc-x" -WebSession $u -UseBasicParsing -SkipHttpErrorCheck; "T04_chat_missing HTTP=$($r.StatusCode) BODY=$($r.Content)" }
# 5 refund apply 缺 orderNo
TryReq "T05_apply_missing" { $r = Invoke-WebRequest "$base/ai/refund/apply?conversationId=acc-x" -Method POST -WebSession $u -UseBasicParsing -SkipHttpErrorCheck; "T05_apply_missing HTTP=$($r.StatusCode) BODY=$($r.Content)" }
# 6 lisi 写入记忆
$r = Invoke-WebRequest "$base/ai/chat?conversationId=acc-lisi-1&message=请记住：我最喜欢的颜色是蓝色。只回复好的" -WebSession $l -UseBasicParsing -TimeoutSec 90
"T06_lisi_seed HTTP=$($r.StatusCode) LEN=$($r.Content.Length)"
# 7 zhangsan 读 lisi 会话（跨用户记忆泄露验证）
$r = Invoke-WebRequest "$base/ai/chat?conversationId=acc-lisi-1&message=我最喜欢的颜色是什么？直接回答颜色" -WebSession $u -UseBasicParsing -TimeoutSec 90
"T07_cross_memory BODY=$($r.Content)"
# 8 zhangsan 查他人订单 10002
$r = Invoke-WebRequest "$base/ai/chat?conversationId=acc-iso-1&message=请使用订单查询工具查询订单10002的物流" -WebSession $u -UseBasicParsing -TimeoutSec 90
"T08_other_order BODY=$($r.Content)"
# 9 zhangsan 查自己订单 10001
$r = Invoke-WebRequest "$base/ai/chat?conversationId=acc-iso-1&message=请使用订单查询工具查询订单10001的物流" -WebSession $u -UseBasicParsing -TimeoutSec 90
"T09_own_order BODY=$($r.Content.Substring(0, [Math]::Min(300, $r.Content.Length)))"
# 10 lisi apply 10002
$r = Invoke-WebRequest "$base/ai/refund/apply?conversationId=acc-ref-1&orderNo=10002" -Method POST -WebSession $l -UseBasicParsing -SkipHttpErrorCheck
"T10_apply BODY=$($r.Content)"
$rid = ($r.Content | ConvertFrom-Json).data.refundId
# 11 zhangsan 越权 confirm
$r = Invoke-WebRequest "$base/ai/refund/confirm?refundId=$rid" -Method POST -WebSession $u -UseBasicParsing -SkipHttpErrorCheck
"T11_cross_confirm BODY=$($r.Content)"
# 12 lisi confirm
$r = Invoke-WebRequest "$base/ai/refund/confirm?refundId=$rid" -Method POST -WebSession $l -UseBasicParsing -SkipHttpErrorCheck
"T12_confirm BODY=$($r.Content)"
# 13 重复 confirm
$r = Invoke-WebRequest "$base/ai/refund/confirm?refundId=$rid" -Method POST -WebSession $l -UseBasicParsing -SkipHttpErrorCheck
"T13_double_confirm BODY=$($r.Content)"
# 14 重复 apply
$r = Invoke-WebRequest "$base/ai/refund/apply?conversationId=acc-ref-1&orderNo=10002" -Method POST -WebSession $l -UseBasicParsing -SkipHttpErrorCheck
"T14_double_apply BODY=$($r.Content)"
# 15 不存在 refundId
$r = Invoke-WebRequest "$base/ai/refund/confirm?refundId=99999" -Method POST -WebSession $l -UseBasicParsing -SkipHttpErrorCheck
"T15_missing_refund BODY=$($r.Content)"
# 16 管理端三态
TryReq "T16a_admin_anon" { $null = Invoke-WebRequest "$base/ai/admin/stats/overview" -UseBasicParsing; "T16a HTTP=200BAD" }
TryReq "T16b_admin_user" { $null = Invoke-WebRequest "$base/ai/admin/stats/overview" -WebSession $u -UseBasicParsing; "T16b HTTP=200BAD" }
$r = Invoke-WebRequest "$base/ai/admin/stats/trends?days=9" -WebSession $a -UseBasicParsing -SkipHttpErrorCheck
"T16c_bad_days HTTP=$($r.StatusCode) BODY=$($r.Content)"
# 17 限流
$cnt = 0; for ($i = 0; $i -lt 40; $i++) { try { $null = Invoke-WebRequest "$base/ai/me" -WebSession $u -UseBasicParsing -TimeoutSec 5 } catch { if ($_.Exception.Response.StatusCode.value__ -eq 429) { $cnt++ } } }
"T17_ratelimit_429count=$cnt"
# 18 未知路径
TryReq "T18_unknown" { $r = Invoke-WebRequest "$base/ai/nope" -WebSession $u -UseBasicParsing -SkipHttpErrorCheck; "T18_unknown HTTP=$($r.StatusCode) BODY=$($r.Content.Substring(0, [Math]::Min(200, $r.Content.Length)))" }
# 19 CORS 头
$r = Invoke-WebRequest "$base/ai/me" -Headers @{ Origin = "http://evil.example" } -WebSession $u -UseBasicParsing -SkipHttpErrorCheck
"T19_cors ACAO=[$($r.Headers['Access-Control-Allow-Origin'])]"
# 20 SSE 中途断开（HttpClient + 2s 取消）
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.CookieContainer = New-Object System.Net.CookieContainer
$hc = New-Object System.Net.Http.HttpClient($handler)
$null = $hc.PostAsync("$base/ai/login?username=zhangsan&password=123456", $null).Result
$cts = New-Object System.Threading.CancellationTokenSource
$cts.CancelAfter(2500)
try {
  $resp = $hc.GetAsync("$base/ai/chat/stream?conversationId=acc-abort-1&message=请写一篇300字的SpringBoot介绍", [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead, $cts.Token).Result
  $sr = New-Object System.IO.StreamReader($resp.Content.ReadAsStreamAsync().Result)
  $n = 0; while (-not $sr.EndOfStream -and $n -lt 5) { $null = $sr.ReadLine(); $n++ }
  "T20_sse_read_lines=$n"
} catch { "T20_sse_cancelled=$($_.Exception.InnerException.GetType().Name)" }
$cts.Dispose(); $hc.Dispose()
Start-Sleep -Seconds 4
"T20_abort_log_rows=$(SQL "select count(*) from ai_request_log where conversation_id='acc-abort-1'")"
"T20_abort_msg_roles=$(SQL "select group_concat(role) from ai_chat_message where conversation_id='acc-abort-1'")"
# 21 DB 核验
"T21_order10002=$(SQL "select status from t_order where order_no='10002'")"
"T21_refunds=$(SQL "select id,order_no,status from ai_refund order by id")"
"DONE"
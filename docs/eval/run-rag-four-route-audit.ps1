param(
    [string]$BaseUrl = "http://127.0.0.1:8091",
    [string]$OutputPath = "docs/eval/latest-rag-four-route-audit.json",
    [string]$Token = $env:AI_EVAL_BEARER_TOKEN
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Set AI_EVAL_BEARER_TOKEN to an isolated evaluation-session token."
}

$headers = @{ Authorization = "Bearer $Token" }
$questions = @(
    @{ id="A1"; expected="INTERNAL"; question="What is the difference between HTTP 401 and HTTP 403, and how does this relate to JWT authentication?" },
    @{ id="A2"; expected="INTERNAL"; question="What are the main troubleshooting steps when Redis cache becomes unavailable in a production environment?" },
    @{ id="A3"; expected="INTERNAL"; question="What should an engineer check when PostgreSQL authentication or role permissions cause access failures?" },
    @{ id="A4"; expected="INTERNAL"; question="What are common causes of Docker container startup failures, and how can they be diagnosed?" },
    @{ id="A5"; expected="INTERNAL"; question="What should be checked when a Kubernetes pod enters CrashLoopBackOff?" },
    @{ id="A6"; expected="INTERNAL"; question="What is the standard incident escalation workflow for a high-severity production outage?" },
    @{ id="B1"; expected="WEB"; question="According to the official Redis documentation, what are keyspace notifications and what configuration enables them?" },
    @{ id="B2"; expected="WEB"; question="According to the official PostgreSQL documentation, what is the purpose of logical replication slots?" },
    @{ id="B3"; expected="WEB"; question="What does the official Kubernetes documentation say about ValidatingAdmissionPolicy?" },
    @{ id="B4"; expected="WEB"; question="What are the currently documented supported Python versions for the latest stable Django release?" },
    @{ id="C1"; expected="AI_ASSISTED_WEB"; question="Compare Redis Sentinel and Redis Cluster for production high availability. Based on official documentation, explain their differences, operational trade-offs, and suitable use cases." },
    @{ id="C2"; expected="AI_ASSISTED_WEB"; question="Using current official Kubernetes documentation, compare RBAC, NetworkPolicy, and admission policies. Explain how they work together in a production security architecture." },
    @{ id="C3"; expected="AI_ASSISTED_WEB"; question="Using official PostgreSQL documentation, analyze the operational differences between physical replication and logical replication, including limitations and failover considerations." },
    @{ id="C4"; expected="AI_ASSISTED_WEB"; question="Based on current official documentation from Kubernetes, Redis, and PostgreSQL, propose a fault-tolerant deployment architecture and explain which recommendations are directly supported by each source." },
    @{ id="D1"; expected="AI_ONLY"; question="Invent three original names for a fictional CloudOps monitoring platform and explain the design concept behind each name." },
    @{ id="D2"; expected="AI_ONLY"; question="Imagine a fictional company operating a cloud platform on Mars. Propose a creative incident response workflow for its engineering team." },
    @{ id="D3"; expected="AI_ONLY"; question="What is the exact internal disaster recovery policy approved by the fictional company NovaOrbit on October 20, 2026?" },
    @{ id="D4"; expected="AI_ONLY"; question="Explain the official specification of the fictional QuantumNimbus-9000 database replication protocol." }
)

function Resolve-Route([string]$answerMode, [string]$sourceType) {
    switch ($answerMode) {
        "INTERNAL_RAG" { return "INTERNAL" }
        "WEB_SEARCH" { return "WEB" }
        "AI_ASSISTED_WEB" { return "AI_ASSISTED_WEB" }
        "AI_ONLY" { return "AI_ONLY" }
        "NONE" { return "NONE" }
    }
    switch ($sourceType) {
        { $_ -in @("INTERNAL", "ARTICLE", "DOCUMENT") } { return "INTERNAL" }
        "WEB" { return "WEB" }
        "AI_ASSISTED_WEB" { return "AI_ASSISTED_WEB" }
        { $_ -in @("AI_ONLY", "LLM") } { return "AI_ONLY" }
        default { return $sourceType }
    }
}

$results = foreach ($item in $questions) {
    $started = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/ai/ask" -Method Post -Headers $headers -ContentType "application/json" -Body (@{question=$item.question}|ConvertTo-Json) -TimeoutSec 120
        $started.Stop()
        $data = $response.data
        $answer = [string]$data.answer
        $route = Resolve-Route ([string]$data.answerMode) ([string]$data.sourceType)
        $noise = $answer -match "Primary keywords|Source references|Use this runbook|Use this playbook|Knowledge Candidate|Observed answer|uploaded document chunks|consolidated reviewed|__next|window\."
        [pscustomobject]@{
            id = $item.id
            question = $item.question
            expectedRoute = $item.expected
            actualRoute = $route
            rawSourceType = $data.sourceType
            answerMode = $data.answerMode
            answerEngine = $data.answerEngine
            retrievalTrace = $data.retrievalTrace
            routeMatches = $route -eq $item.expected
            noiseFree = -not $noise
            answerLength = $answer.Length
            latencyMs = $started.ElapsedMilliseconds
            sourceUrl = $data.sourceUrl
            sources = $data.sources
            answer = $answer
            status = if ($route -eq $item.expected -and -not $noise) { "PASS" } else { "FAIL" }
        }
    } catch {
        $started.Stop()
        [pscustomobject]@{
            id = $item.id
            question = $item.question
            expectedRoute = $item.expected
            actualRoute = "ERROR"
            rawSourceType = "ERROR"
            answerEngine = $null
            routeMatches = $false
            noiseFree = $false
            answerLength = 0
            latencyMs = $started.ElapsedMilliseconds
            sourceUrl = $null
            sources = @()
            answer = $_.Exception.Message
            status = "FAIL"
        }
    }
}

$directory = Split-Path -Parent $OutputPath
if ($directory -and -not (Test-Path $directory)) {
    New-Item -Path $directory -ItemType Directory | Out-Null
}
$results | ConvertTo-Json -Depth 8 | Set-Content -Path $OutputPath -Encoding UTF8
$results | Select-Object id,expectedRoute,actualRoute,answerEngine,routeMatches,noiseFree,answerLength,latencyMs,status | Format-Table -AutoSize

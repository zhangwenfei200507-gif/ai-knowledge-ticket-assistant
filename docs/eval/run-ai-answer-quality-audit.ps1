param(
    [string]$BaseUrl = "http://localhost:8090",
    [string]$OutputPath = "docs/eval/latest-ai-answer-quality-audit.json",
    [string]$Token = $env:AI_EVAL_BEARER_TOKEN
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw "Set AI_EVAL_BEARER_TOKEN to an administrator OAuth session token before running this evaluation."
}

$headers = @{ Authorization = "Bearer $token" }

$questions = @(
    @{ domain = "Auth / JWT"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What should I check when a Spring Security JWT API returns 401 or 403?" },
    @{ domain = "Auth / JWT"; kind = "Web"; expectedSource = "WEB"; question = "What is JWT DPoP proof-of-possession and how is it different from a bearer token?" },
    @{ domain = "Docker"; kind = "Internal"; expectedSource = "ARTICLE"; question = "Why should Docker Compose services use service names instead of localhost for container-to-container communication?" },
    @{ domain = "Docker"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What is the purpose of Docker multi-stage builds, and when should they be used?" },
    @{ domain = "Docker"; kind = "Internal"; expectedSource = "ARTICLE"; question = "How does Docker BuildKit improve image build performance?" },
    @{ domain = "Docker"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What is the difference between ARG and ENV in a Dockerfile, and why should I use a .dockerignore file?" },
    @{ domain = "Docker"; kind = "Web"; expectedSource = "WEB"; question = "What is Docker Bake and when should a team use it instead of plain docker build commands?" },
    @{ domain = "Incident SOP"; kind = "Internal"; expectedSource = "ARTICLE"; question = "How should I classify an incident as P1, P2, or P3?" },
    @{ domain = "Incident SOP"; kind = "Web"; expectedSource = "WEB"; question = "What is the difference between SEV0 and SEV1 in incident management?" },
    @{ domain = "Customer Templates"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What should administrators check before publishing knowledge into the internal knowledge base?" },
    @{ domain = "Customer Templates"; kind = "Web"; expectedSource = "WEB"; question = "How do RAG teams measure retrieval quality with precision at k and recall at k?" },
    @{ domain = "Kubernetes"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What is the difference between readiness, liveness, and startup probes in Kubernetes?" },
    @{ domain = "Kubernetes"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What is Kubernetes Gateway API HTTPRoute and when should it replace Ingress?" },
    @{ domain = "Kubernetes"; kind = "Web"; expectedSource = "WEB"; question = "What is the Kubernetes ValidatingAdmissionPolicy feature used for?" },
    @{ domain = "PostgreSQL"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What should I check when PostgreSQL connections time out or the pool is exhausted?" },
    @{ domain = "PostgreSQL"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What does pg_stat_io show in PostgreSQL 16 and how is it useful?" },
    @{ domain = "PostgreSQL"; kind = "Web"; expectedSource = "WEB"; question = "What is the purpose of PostgreSQL logical replication failover slots?" },
    @{ domain = "Redis Cache"; kind = "Internal"; expectedSource = "ARTICLE"; question = "What should I check when Redis latency spikes or keys disappear?" },
    @{ domain = "Redis Cache"; kind = "Internal"; expectedSource = "ARTICLE"; question = "How does Redis client-side caching with server-assisted tracking work?" },
    @{ domain = "Redis Cache"; kind = "Web"; expectedSource = "WEB"; question = "What is Redis keyspace notification and when should it be enabled?" },
    @{ domain = "Ticket Rules"; kind = "Internal"; expectedSource = "ARTICLE"; question = "How should I determine whether a support ticket is P1, P2, or P3?" },
    @{ domain = "Ticket Rules"; kind = "Web"; expectedSource = "WEB"; question = "How does an ITIL impact and urgency matrix calculate incident priority?" }
)

$results = foreach ($item in $questions) {
    $body = @{ question = $item.question } | ConvertTo-Json
    try {
        $response = Invoke-RestMethod -Uri "$BaseUrl/api/ai/ask" -Method Post -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 90
        $answer = [string]$response.data.answer
        $noise = $answer -match "Primary keywords|Source references|Use this runbook|Use this playbook|Knowledge Candidate|Observed answer|uploaded document chunks|consolidated reviewed|window\.|__next"
        [pscustomobject]@{
            domain = $item.domain
            kind = $item.kind
            question = $item.question
            expectedSource = $item.expectedSource
            actualSource = $response.data.sourceType
            sourceMatches = $response.data.sourceType -eq $item.expectedSource
            noiseFree = -not $noise
            answerLength = $answer.Length
            preview = (($answer -replace "\r?\n", " ") | ForEach-Object { $_.Substring(0, [Math]::Min(220, $_.Length)) })
        }
    } catch {
        [pscustomobject]@{
            domain = $item.domain
            kind = $item.kind
            question = $item.question
            expectedSource = $item.expectedSource
            actualSource = "ERROR"
            sourceMatches = $false
            noiseFree = $false
            answerLength = 0
            preview = $_.Exception.Message
        }
    }
}

$directory = Split-Path -Parent $OutputPath
if ($directory -and -not (Test-Path $directory)) {
    New-Item -Path $directory -ItemType Directory | Out-Null
}

$results | ConvertTo-Json -Depth 5 | Set-Content -Path $OutputPath -Encoding UTF8
$results | Format-Table domain, kind, expectedSource, actualSource, sourceMatches, noiseFree -AutoSize

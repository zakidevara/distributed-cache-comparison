# ============================================================
# Hazelcast Near-Cache Auto-Refresh Demo Script
# ============================================================
# This script demonstrates how Hazelcast Near-Cache provides
# automatic refresh through invalidation (unlike Hollow polling).
#
# Prerequisites:
# 1. Start app: ./gradlew bootRun --args='--spring.profiles.active=hazelcast'
# ============================================================

$baseUrl = "http://localhost:8080/api/hazelcast"

function Write-ColorText {
    param([string]$Text, [string]$Color = "White")
    Write-Host $Text -ForegroundColor $Color
}

function Invoke-ApiCall {
    param([string]$Method, [string]$Uri, [object]$Body = $null)
    try {
        $params = @{
            Method = $Method
            Uri = $Uri
            ContentType = "application/json"
        }
        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
        }
        $response = Invoke-RestMethod @params
        return $response
    }
    catch {
        Write-ColorText "Error: $($_.Exception.Message)" "Red"
        return $null
    }
}

# ============================================================
# Demo Start
# ============================================================
Write-ColorText "`n╔══════════════════════════════════════════════════════════╗" "Cyan"
Write-ColorText "║       Hazelcast Near-Cache Auto-Refresh Demo             ║" "Cyan"
Write-ColorText "╚══════════════════════════════════════════════════════════╝" "Cyan"

# Check status
Write-ColorText "`n[1/6] Checking Hazelcast status..." "Yellow"
$status = Invoke-ApiCall -Method GET -Uri "$baseUrl/status"
if ($status) {
    Write-ColorText "State: $($status.state)" "Green"
    Write-ColorText "Ready: $($status.ready)" "Green"
    Write-ColorText "Current Count: $($status.count)" "Green"
    if ($status.nearCacheStats) {
        Write-ColorText "Near-Cache Stats:" "Cyan"
        Write-ColorText "  Hits: $($status.nearCacheStats.hits)" "White"
        Write-ColorText "  Misses: $($status.nearCacheStats.misses)" "White"
        Write-ColorText "  Hit Ratio: $([math]::Round($status.nearCacheStats.hitRatio * 100, 2))%" "White"
    }
}

# Clear existing data
Write-ColorText "`n[2/6] Clearing existing data..." "Yellow"
$result = Invoke-ApiCall -Method DELETE -Uri "$baseUrl/clear"
if ($result) {
    Write-ColorText "Data cleared: $($result.message)" "Green"
}

# Publish initial data
Write-ColorText "`n[3/6] Publishing initial users..." "Yellow"
$users = @(
    @{ id = 100; username = "hazel_user_1"; active = $true },
    @{ id = 101; username = "hazel_user_2"; active = $true },
    @{ id = 102; username = "hazel_user_3"; active = $false }
)
$result = Invoke-ApiCall -Method POST -Uri "$baseUrl/publish" -Body $users
if ($result) {
    Write-ColorText "Published $($result.recordCount) records" "Green"
    Write-ColorText "Note: $($result.note)" "Cyan"
}

Start-Sleep -Milliseconds 500

# Query - first read populates Near-Cache
Write-ColorText "`n[4/6] Querying users (first read populates Near-Cache)..." "Yellow"
$allUsers = Invoke-ApiCall -Method GET -Uri "$baseUrl/users"
if ($allUsers) {
    Write-ColorText "Found $($allUsers.Count) users:" "Green"
    $allUsers | ForEach-Object {
        $activeIcon = if ($_.active) { "✓" } else { "✗" }
        Write-ColorText "  [$activeIcon] ID: $($_.id), Username: $($_.username)" "White"
    }
}

# Show Near-Cache stats after first read
Write-ColorText "`nChecking Near-Cache after first read..." "Yellow"
$status = Invoke-ApiCall -Method GET -Uri "$baseUrl/status"
if ($status -and $status.nearCacheStats) {
    Write-ColorText "  Hits: $($status.nearCacheStats.hits) | Misses: $($status.nearCacheStats.misses)" "Cyan"
}

# Update a user - Near-Cache will be invalidated
Write-ColorText "`n[5/6] Updating user 101 (Near-Cache will be invalidated)..." "Yellow"
$updatedUser = @{ id = 101; username = "hazel_user_2_UPDATED"; active = $false }
$result = Invoke-ApiCall -Method POST -Uri "$baseUrl/users" -Body $updatedUser
if ($result) {
    Write-ColorText "Updated user: $($result.user.username)" "Green"
}

Start-Sleep -Milliseconds 200

# Query again - should see updated data (Near-Cache was invalidated)
Write-ColorText "`n[6/6] Querying again (Near-Cache invalidated, fetches fresh data)..." "Yellow"
$user = Invoke-ApiCall -Method GET -Uri "$baseUrl/users/101"
if ($user) {
    Write-ColorText "User 101 now:" "Green"
    Write-ColorText "  Username: $($user.username)" "White"
    Write-ColorText "  Active: $($user.active)" "White"
}

# Final Near-Cache stats
Write-ColorText "`nFinal Near-Cache statistics:" "Yellow"
$status = Invoke-ApiCall -Method GET -Uri "$baseUrl/status"
if ($status -and $status.nearCacheStats) {
    Write-ColorText "  Hits: $($status.nearCacheStats.hits)" "Cyan"
    Write-ColorText "  Misses: $($status.nearCacheStats.misses)" "Cyan"
    Write-ColorText "  Invalidations: $($status.nearCacheStats.invalidations)" "Cyan"
    Write-ColorText "  Hit Ratio: $([math]::Round($status.nearCacheStats.hitRatio * 100, 2))%" "Green"
}

# Summary
Write-ColorText "`n╔══════════════════════════════════════════════════════════╗" "Cyan"
Write-ColorText "║                      Demo Complete!                       ║" "Cyan"
Write-ColorText "╠══════════════════════════════════════════════════════════╣" "Cyan"
Write-ColorText "║  Hazelcast Near-Cache Benefits:                          ║" "Cyan"
Write-ColorText "║  • Ultra-fast local reads (sub-millisecond)              ║" "Green"
Write-ColorText "║  • Automatic invalidation on updates                     ║" "Green"
Write-ColorText "║  • No manual refresh calls needed                        ║" "Green"
Write-ColorText "║  • Built-in statistics for monitoring                    ║" "Green"
Write-ColorText "╚══════════════════════════════════════════════════════════╝" "Cyan"

Write-ColorText "`nComparison with other implementations:" "Yellow"
Write-ColorText "  Hollow:    File watcher + triggerRefresh() (~1-5ms)" "White"
Write-ColorText "  Kafka:     KTable auto-update from stream (~50-100ms)" "White"
Write-ColorText "  Hazelcast: Near-Cache invalidation (~0.1-1ms)" "Green"

Write-ColorText "`nAvailable endpoints:" "Yellow"
Write-ColorText "  GET  $baseUrl/users         - List all users" "White"
Write-ColorText "  GET  $baseUrl/users/{id}    - Get user by ID" "White"
Write-ColorText "  POST $baseUrl/users         - Create/update user" "White"
Write-ColorText "  DELETE $baseUrl/users/{id}  - Delete user" "White"
Write-ColorText "  GET  $baseUrl/status        - Check status + Near-Cache stats" "White"

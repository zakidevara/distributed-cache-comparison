# Redis Auto-Refresh Demo Script
# Demonstrates Redis client-side caching with automatic invalidation

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Redis Client-Side Caching Demo" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080/api/redis"

# Function to make REST calls
function Invoke-Api {
    param (
        [string]$Method,
        [string]$Endpoint,
        [object]$Body = $null
    )
    
    $uri = "$baseUrl$Endpoint"
    $params = @{
        Method = $Method
        Uri = $uri
        ContentType = "application/json"
    }
    
    if ($Body) {
        $params.Body = ($Body | ConvertTo-Json)
    }
    
    try {
        $response = Invoke-RestMethod @params
        return $response
    } catch {
        Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
        return $null
    }
}

# Check if application is running
Write-Host "Checking if application is running..." -ForegroundColor Yellow
try {
    $null = Invoke-RestMethod -Uri "$baseUrl/count" -Method GET -TimeoutSec 5
    Write-Host "Application is running!" -ForegroundColor Green
} catch {
    Write-Host "Application not running. Please start with:" -ForegroundColor Red
    Write-Host "  .\gradlew bootRun --args='--spring.profiles.active=redis'" -ForegroundColor White
    Write-Host ""
    Write-Host "Also ensure Redis is running:" -ForegroundColor Red
    Write-Host "  docker-compose -f docker-compose-redis.yml up -d" -ForegroundColor White
    exit 1
}

Write-Host ""

# Step 1: Clear existing data
Write-Host "Step 1: Clearing existing data..." -ForegroundColor Yellow
$result = Invoke-Api -Method DELETE -Endpoint "/produce/all"
Write-Host "  Data cleared" -ForegroundColor Green
Write-Host ""

# Step 2: Produce sample data
Write-Host "Step 2: Producing sample data (100 users)..." -ForegroundColor Yellow
$result = Invoke-Api -Method POST -Endpoint "/produce/sample?count=100"
Write-Host "  Produced $($result.count) users to Redis" -ForegroundColor Green
Write-Host ""

# Step 3: Check initial counts
Write-Host "Step 3: Checking counts..." -ForegroundColor Yellow
$count = Invoke-Api -Method GET -Endpoint "/count"
Write-Host "  Redis count: $($count.redisCount)" -ForegroundColor Cyan
Write-Host "  Local cache: $($count.localCacheSize)" -ForegroundColor Cyan
Write-Host ""

# Step 4: Read some users to populate local cache
Write-Host "Step 4: Reading users to populate local cache..." -ForegroundColor Yellow
for ($i = 1; $i -le 10; $i++) {
    $user = Invoke-Api -Method GET -Endpoint "/consume/$i"
    if ($user) {
        Write-Host "  User $i`: $($user.user.username) (fromCache: $($user.fromLocalCache))" -ForegroundColor Gray
    }
}
Write-Host ""

# Step 5: Check cache stats
Write-Host "Step 5: Cache statistics after reads..." -ForegroundColor Yellow
$stats = Invoke-Api -Method GET -Endpoint "/consume/stats"
Write-Host "  Local cache size: $($stats.localCacheSize)" -ForegroundColor Cyan
Write-Host ""

# Step 6: Read from local cache only
Write-Host "Step 6: Reading from local cache only..." -ForegroundColor Yellow
$localUser = Invoke-Api -Method GET -Endpoint "/consume/local/5"
Write-Host "  User 5 from local cache: $($localUser.status)" -ForegroundColor Cyan
if ($localUser.user) {
    Write-Host "    Email: $($localUser.user.email)" -ForegroundColor Gray
}
Write-Host ""

# Step 7: Demonstrate invalidation
Write-Host "Step 7: Demonstrating automatic cache invalidation..." -ForegroundColor Yellow
Write-Host "  Before update: Reading user 5..." -ForegroundColor Gray
$before = Invoke-Api -Method GET -Endpoint "/consume/5"
Write-Host "    Username: $($before.user.username)" -ForegroundColor Gray
Write-Host "    In local cache: $($before.fromLocalCache)" -ForegroundColor Gray

Write-Host ""
Write-Host "  Updating user 5..." -ForegroundColor Gray
$updateBody = @{
    username = "modified_user5"
    active = $true
}
$updateResult = Invoke-Api -Method PUT -Endpoint "/produce/5" -Body $updateBody
Write-Host "    Update result: $($updateResult.message)" -ForegroundColor Gray

Start-Sleep -Milliseconds 100

Write-Host ""
Write-Host "  After update: Reading user 5..." -ForegroundColor Gray
$after = Invoke-Api -Method GET -Endpoint "/consume/5"
Write-Host "    Username: $($after.user.username)" -ForegroundColor Green
Write-Host "    In local cache: $($after.fromLocalCache)" -ForegroundColor Gray
Write-Host ""

# Step 8: Run the built-in invalidation demo
Write-Host "Step 8: Running built-in invalidation demo for user 10..." -ForegroundColor Yellow
$demo = Invoke-Api -Method POST -Endpoint "/demo/invalidation/10"
Write-Host "  Before update:" -ForegroundColor Gray
Write-Host "    Username: $($demo.step1_beforeUpdate.username)" -ForegroundColor Gray
Write-Host "    Was in cache: $($demo.step1_wasInCache)" -ForegroundColor Gray
Write-Host "  After update:" -ForegroundColor Gray
Write-Host "    Username: $($demo.step3_afterUpdate.username)" -ForegroundColor Green
Write-Host "    Is in cache: $($demo.step3_isInCache)" -ForegroundColor Gray
Write-Host "  Invalidation worked: $($demo.invalidationWorked)" -ForegroundColor $(if ($demo.invalidationWorked) { "Green" } else { "Red" })
Write-Host ""

# Step 9: Final stats
Write-Host "Step 9: Final statistics..." -ForegroundColor Yellow
$finalCount = Invoke-Api -Method GET -Endpoint "/count"
$finalStats = Invoke-Api -Method GET -Endpoint "/consume/stats"
Write-Host "  Redis count: $($finalCount.redisCount)" -ForegroundColor Cyan
Write-Host "  Local cache size: $($finalStats.localCacheSize)" -ForegroundColor Cyan
Write-Host ""

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Demo Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Key Observations:" -ForegroundColor Yellow
Write-Host "  - First reads populate the local cache" -ForegroundColor Gray
Write-Host "  - Subsequent reads hit local cache (sub-ms latency)" -ForegroundColor Gray
Write-Host "  - Updates trigger automatic cache invalidation" -ForegroundColor Gray
Write-Host "  - Next read fetches fresh data from Redis" -ForegroundColor Gray
Write-Host ""
Write-Host "To view Redis data:" -ForegroundColor Yellow
Write-Host "  - Redis Commander: http://localhost:8081" -ForegroundColor Cyan

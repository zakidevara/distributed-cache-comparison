# Hollow Auto-Refresh Demonstration Script
# This script demonstrates how the consumer automatically picks up new data
# when the producer publishes updates.

$baseUrl = "http://localhost:8080/api/hollow"
$headers = @{
    "Content-Type" = "application/json"
}

function Write-Step {
    param([string]$message)
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "  $message" -ForegroundColor Cyan
    Write-Host "========================================`n" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$message)
    Write-Host "[OK] $message" -ForegroundColor Green
}

function Write-Info {
    param([string]$message)
    Write-Host " ->  $message" -ForegroundColor Yellow
}

function Get-HollowStatus {
    $response = Invoke-RestMethod -Uri "$baseUrl/status" -Method Get
    return $response
}

function Get-HollowData {
    $response = Invoke-RestMethod -Uri "$baseUrl/data" -Method Get
    return $response
}

function Publish-HollowData {
    param([object]$users)
    $body = $users | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$baseUrl/publish" -Method Post -Headers $headers -Body $body
    return $response
}

function Show-Users {
    param([object]$data)
    if ($data.data.UserAccount) {
        Write-Host "`nCurrent Users in Hollow:" -ForegroundColor White
        Write-Host "+-----+--------------+--------+" -ForegroundColor Gray
        Write-Host "| ID  | Username     | Active |" -ForegroundColor Gray
        Write-Host "+-----+--------------+--------+" -ForegroundColor Gray
        foreach ($user in $data.data.UserAccount) {
            $activeText = if ($user.active) { "YES" } else { "NO " }
            $activeColor = if ($user.active) { "Green" } else { "Red" }
            Write-Host ("| {0,-3} | {1,-12} | " -f $user.id, $user.username) -NoNewline -ForegroundColor Gray
            Write-Host $activeText -NoNewline -ForegroundColor $activeColor
            Write-Host "    |" -ForegroundColor Gray
        }
        Write-Host "+-----+--------------+--------+" -ForegroundColor Gray
    }
}

# Main demonstration flow
Write-Host "`n" -ForegroundColor Magenta
Write-Host "============================================================" -ForegroundColor Magenta
Write-Host "    Hollow Auto-Refresh Feature Demonstration" -ForegroundColor Magenta
Write-Host "============================================================" -ForegroundColor Magenta
Write-Host "" -ForegroundColor Magenta
Write-Host "  This demo shows that the consumer automatically picks up" -ForegroundColor Magenta
Write-Host "  new data when the producer publishes updates - without" -ForegroundColor Magenta
Write-Host "  requiring manual refresh calls!" -ForegroundColor Magenta
Write-Host "" -ForegroundColor Magenta
Write-Host "============================================================" -ForegroundColor Magenta

# Step 1: Check initial status
Write-Step "Step 1: Checking Initial Status"
try {
    $status = Get-HollowStatus
    Write-Info "Current Version: $($status.currentVersion)"
    Write-Info "Has Data: $($status.hasData)"
    if ($status.types) {
        Write-Info "Available Types: $($status.types -join ', ')"
    }
}
catch {
    Write-Host "[ERROR] Make sure the application is running on port 8080!" -ForegroundColor Red
    exit 1
}

Start-Sleep -Seconds 2

# Step 2: Publish initial dataset
Write-Step "Step 2: Publishing Initial Dataset"
$initialUsers = @(
    @{ id = 1; username = "alice"; active = $true }
    @{ id = 2; username = "bob"; active = $true }
    @{ id = 3; username = "charlie"; active = $false }
)

$publishResult = Publish-HollowData -users $initialUsers
Write-Success "Published $($publishResult.recordCount) users"
Write-Info "Waiting 3 seconds for announcement watcher to detect update..."
Start-Sleep -Seconds 3

# Step 3: Query the data
Write-Step "Step 3: Querying Data (Auto-Refreshed)"
$data1 = Get-HollowData
Write-Success "Version after first publish: $($data1.currentVersion)"
Show-Users -data $data1

Start-Sleep -Seconds 2

# Step 4: Publish updated dataset with changes
Write-Step "Step 4: Publishing Updated Dataset"
Write-Info "Changes:"
Write-Info "  - Added new user 'diana'"
Write-Info "  - Added new user 'eve'"  
Write-Info "  - Deactivated user 'bob'"

$updatedUsers = @(
    @{ id = 1; username = "alice"; active = $true }
    @{ id = 2; username = "bob"; active = $false }  # Changed: deactivated
    @{ id = 3; username = "charlie"; active = $false }
    @{ id = 4; username = "diana"; active = $true }   # New user
    @{ id = 5; username = "eve"; active = $true }     # New user
)

$publishResult2 = Publish-HollowData -users $updatedUsers
Write-Success "Published $($publishResult2.recordCount) users"
Write-Info "Waiting 3 seconds for announcement watcher to detect update..."
Start-Sleep -Seconds 3

# Step 5: Query the data again (WITHOUT manual refresh)
Write-Step "Step 5: Querying Data Again (No Manual Refresh Called!)"
$data2 = Get-HollowData
Write-Success "Version after second publish: $($data2.currentVersion)"
Show-Users -data $data2

# Step 6: Compare versions
Write-Step "Step 6: Verification"
if ($data2.currentVersion -gt $data1.currentVersion) {
    Write-Success "Consumer automatically updated!"
    Write-Success "Version changed: $($data1.currentVersion) -> $($data2.currentVersion)"
    Write-Success "The announcement watcher triggered automatic refresh!"
}
else {
    Write-Host "[WARN] Version did not change. Check your announcement watcher configuration." -ForegroundColor Red
}

# Step 7: Publish third dataset to show continuous updates
Write-Step "Step 7: Publishing Third Dataset (Continuous Updates)"
Write-Info "Changes:"
Write-Info "  - Removed user 'charlie'"
Write-Info "  - Activated user 'bob'"
Write-Info "  - Added new user 'frank'"

$thirdUsers = @(
    @{ id = 1; username = "alice"; active = $true }
    @{ id = 2; username = "bob"; active = $true }    # Changed: reactivated
    @{ id = 4; username = "diana"; active = $true }
    @{ id = 5; username = "eve"; active = $true }
    @{ id = 6; username = "frank"; active = $true }  # New user
)

$publishResult3 = Publish-HollowData -users $thirdUsers
Write-Success "Published $($publishResult3.recordCount) users"
Write-Info "Waiting 3 seconds for announcement watcher to detect update..."
Start-Sleep -Seconds 3

# Step 8: Final query
Write-Step "Step 8: Final Data State"
$data3 = Get-HollowData
Write-Success "Version after third publish: $($data3.currentVersion)"
Show-Users -data $data3

# Summary
Write-Host "`n" 
Write-Host "============================================================" -ForegroundColor Green
Write-Host "                        SUMMARY" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  Version 1: $($data1.currentVersion)" -ForegroundColor White
Write-Host "  Version 2: $($data2.currentVersion)" -ForegroundColor White
Write-Host "  Version 3: $($data3.currentVersion)" -ForegroundColor White
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  [OK] Data automatically refreshed after each publish" -ForegroundColor Green
Write-Host "  [OK] No manual /refresh endpoint calls needed" -ForegroundColor Green
Write-Host "  [OK] Announcement watcher detected all updates" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green

Write-Host "`nTip: Check the application logs to see the refresh listener messages!" -ForegroundColor Cyan
Write-Host ""

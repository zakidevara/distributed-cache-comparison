# Hollow Auto-Refresh Demo

This directory contains a demonstration script that showcases Netflix Hollow's automatic consumer refresh feature.

## What This Demonstrates

The script proves that when configured with an `AnnouncementWatcher`, Hollow consumers automatically pick up new data versions **without requiring manual refresh calls**.

## Prerequisites

1. **Start the application** with the filesystem profile:
   ```powershell
   ./gradlew bootRun --args='--spring.profiles.active=filesystem'
   ```

2. Wait for the application to fully start (you should see "Started HollowApplication...")

## Running the Demo

In a new terminal window:

```powershell
./demo-auto-refresh.ps1
```

## What the Script Does

1. **Checks initial status** - Verifies the application is running
2. **Publishes initial dataset** - 3 users (alice, bob, charlie)
3. **Queries the data** - Shows the initial state
4. **Publishes updated dataset** - 5 users (deactivated bob, added diana & eve)
5. **Queries again WITHOUT calling /refresh** - Shows automatic update
6. **Publishes third dataset** - More changes (removed charlie, reactivated bob, added frank)
7. **Final query** - Shows continuous auto-refresh working
8. **Displays summary** - Compares all versions

## Expected Behavior

### ✅ Success Indicators

- Version numbers increase with each publish
- Consumer data reflects all changes automatically
- No manual `/api/hollow/refresh` calls are made
- Application logs show refresh listener messages:
  ```
  Consumer refresh started: [oldVersion] -> [newVersion]
  Consumer refresh successful: [oldVersion] -> [newVersion]
  ```

### Example Output

```
╔═══════════════════════════════════════════════════════════════╗
║                         SUMMARY                               ║
╠═══════════════════════════════════════════════════════════════╣
║  Version 1: 1738347123456                                     ║
║  Version 2: 1738347128789                                     ║
║  Version 3: 1738347134012                                     ║
╠═══════════════════════════════════════════════════════════════╣
║  ✓ Data automatically refreshed after each publish           ║
║  ✓ No manual /refresh endpoint calls needed                  ║
║  ✓ Announcement watcher detected all updates                 ║
╚═══════════════════════════════════════════════════════════════╝
```

## How It Works

### Publisher Side
1. Producer writes new data snapshots/deltas to `hollow-repo/`
2. Producer updates the announcement file with new version

### Consumer Side
1. `HollowFilesystemAnnouncementWatcher` monitors announcement file
2. When new version detected, automatically calls `consumer.triggerRefresh()`
3. `RefreshListener` logs the refresh events
4. Consumer state engine updates to new version

### Code Components

- **`FilesystemConsumerService`** - Configured with:
  - `HollowFilesystemBlobRetriever` - Reads data blobs
  - `HollowFilesystemAnnouncementWatcher` - Monitors for updates
  - `AbstractRefreshListener` - Logs refresh events

- **No polling needed** - File system watcher is event-driven

## Troubleshooting

### Version doesn't change
- Check application logs for errors
- Verify `hollow-repo/` directory exists and is writable
- Ensure announcement watcher is configured correctly

### Connection errors
- Verify application is running on port 8080
- Check firewall settings

### Data doesn't update
- Increase wait time in script (change `Start-Sleep` values)
- Monitor application logs for refresh events
- Check that announcement file is being updated

## Manual Testing

You can also test manually with curl:

```powershell
# Publish data
curl -X POST http://localhost:8080/api/hollow/publish -H "Content-Type: application/json" -d '[{"userId":1,"username":"test","active":true}]'

# Wait 3 seconds, then query (should show new data automatically)
curl http://localhost:8080/api/hollow/data

# Check status and version
curl http://localhost:8080/api/hollow/status
```

## Key Takeaway

🎯 **With `AnnouncementWatcher` configured, Hollow consumers automatically refresh when new data is published - no manual intervention required!**

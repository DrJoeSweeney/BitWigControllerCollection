# FaderFox PC12 Extension — Debugging Plan

## Step 1: Confirm the file is in place

On the Windows machine, verify the file exists:

```
C:\Program Files\Bitwig Studio\Resources\Extensions\faderfox-pc12.bwextension
```

## Step 2: Check Bitwig's log file

Open this file in a text editor (e.g. Notepad):

```
%LOCALAPPDATA%\Bitwig Studio\log\bitwig-studio.log
```

Typical path:

```
C:\Users\<YourName>\AppData\Local\Bitwig Studio\log\bitwig-studio.log
```

Search for:
- `faderfox`
- `DocJoe`
- `Exception`
- `Error`
- `Failed to load`

Copy any matching lines — these will explain why the extension isn't loading.

## Step 3: Check the Controller Script Console

1. Open Bitwig Studio
2. Go to **View → Controller Script Console**
3. Look for a tab named "FaderFox PC12" or "DocJoe"
4. If present, copy any error messages or stack traces

## Step 4: Verify the extension appears in the controller list

1. Go to **Settings → Controllers → + Add Controller**
2. Scroll through the vendor list looking for **DocJoe**
3. If not found, the extension failed to load — the log file (Step 2) will have the reason

## Step 5: Check API version compatibility

The extension requires Bitwig API version 18. To check your Bitwig version:

1. Go to **Help → About Bitwig Studio**
2. Note the version number
3. Bitwig 5.x supports API 18. If running an older version, this may be the issue.

## Step 6: Try the user Extensions folder instead

If the `Program Files` folder doesn't work, try copying to the user-level Extensions folder:

```
%USERPROFILE%\Documents\Bitwig Studio\Extensions\faderfox-pc12.bwextension
```

This is the standard deploy location for user-installed extensions on Windows.

## What to report back

Please copy and paste:
1. Any error lines from `bitwig-studio.log` mentioning faderfox/DocJoe/Exception
2. Any output from the Controller Script Console
3. Your Bitwig Studio version number

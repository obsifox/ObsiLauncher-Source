#!/usr/bin/env python3
"""
Fetches the official Wilderness Bound trailer as a local MP4 for the
ObsiLauncher video background (latest release only).

The launcher plays the file from:
    <data>/shared/wallpapers/video/trailer.mp4
(this script writes it there automatically when OBSI_HOME is set, otherwise
copy the output manually).

Requires:  pip install pytube
Usage:     python3 fetch-trailer.py
"""
import pathlib
import sys

VIDEO_URL = "https://www.youtube.com/watch?v=1HCrV7mFWr8"


def target_dir() -> pathlib.Path:
    import os
    home = os.environ.get("OBSI_HOME")
    if home:
        return pathlib.Path(home) / "shared" / "wallpapers" / "video"
    if os.name == "nt":
        base = pathlib.Path(os.environ.get("APPDATA", pathlib.Path.home())) / "ObsiLauncher"
    else:
        base = pathlib.Path(os.environ.get("XDG_DATA_HOME", pathlib.Path.home() / ".local/share")) / "ObsiLauncher"
    return base / "shared" / "wallpapers" / "video"


def main() -> int:
    try:
        from pytube import YouTube
    except ImportError:
        print("pytube is required:  pip install pytube")
        return 1

    out_dir = target_dir()
    out_dir.mkdir(parents=True, exist_ok=True)
    out_file = out_dir / "trailer.mp4"

    print(f"Downloading {VIDEO_URL}")
    stream = (
        YouTube(VIDEO_URL)
        .streams.filter(progressive=True, file_extension="mp4")
        .order_by("resolution").desc()
        .first()
    )
    if stream is None:
        print("No suitable MP4 stream found.")
        return 1
    print(f"-> {stream.resolution} to {out_file}")
    stream.download(filename=str(out_file))
    print("Done. Restart ObsiLauncher and enable Settings > Appearance > Video background.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""Convert a local test video into the JVM scroll fixture; never used by the Android app.
Requires opencv-python-headless and numpy in the developer Python environment.
"""
import argparse
import struct
from pathlib import Path

import cv2
import numpy as np

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("video", type=Path)
parser.add_argument("output", type=Path)
parser.add_argument("--rect", type=int, nargs=4, required=True, metavar=("LEFT", "TOP", "WIDTH", "HEIGHT"))
parser.add_argument("--row-height", type=int, required=True)
parser.add_argument("--fps", type=float, default=60)
args = parser.parse_args()
left, top, width, height = args.rect
if min(left, top) < 0 or min(width, height, args.row_height, args.fps) <= 0:
    parser.error("Invalid region, row height or sample rate")
output_width = 160
output_height = max(16, min(600, round(height * output_width / width)))
row_height = max(8, round(args.row_height * output_height / height))
video = cv2.VideoCapture(str(args.video))
if not video.isOpened():
    parser.error("Cannot open video")
def reduce(image):
    if top + height > image.shape[0] or left + width > image.shape[1]:
        parser.error("Region exceeds the video frame")
    roi = image[top:top + height, left:left + width].astype(np.int32)
    gray = (roi[:, :, 2] * 299 + roi[:, :, 1] * 587 + roi[:, :, 0] * 114) // 1000
    reduced = np.zeros((output_height, output_width), dtype=np.int32)
    for dy in range(2):
        for dx in range(2):
            xs = ((np.arange(output_width) + .25 + dx * .5) * width / output_width).astype(int)
            ys = ((np.arange(output_height) + .25 + dy * .5) * height / output_height).astype(int)
            reduced += gray[np.ix_(ys, xs)]
    return (reduced // 4).astype(np.uint8).tobytes()

frames = []
next_sample = 0.0
previous = None
while True:
    ok, image = video.read()
    if not ok:
        break
    time = video.get(cv2.CAP_PROP_POS_MSEC) / 1000
    current = reduce(image)
    # Fixed-clock requests see the most recent available frame, including repeated static frames.
    while next_sample <= time + 1e-9:
        frames.append((int(next_sample * 1e9) + 1, previous if previous is not None else current))
        next_sample += 1 / args.fps
    previous = current
video.release()
args.output.parent.mkdir(parents=True, exist_ok=True)
with args.output.open("wb") as result:
    result.write(struct.pack(">iiii", output_width, output_height, row_height, len(frames)))
    for time, pixels in frames:
        result.write(struct.pack(">q", time))
        result.write(pixels)
print(f"Prepared {len(frames)} frames, {output_width}x{output_height}, row height {row_height}")

import urllib.request
import os

output_dir = "app/src/main/assets"
output_path = os.path.join(output_dir, "facenet.tflite")

if not os.path.exists(output_dir):
    os.makedirs(output_dir)

try:
    print(f"Downloading FaceNet model from public repo...")
    url = "https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android/raw/master/app/src/main/assets/facenet.tflite"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req) as response, open(output_path, 'wb') as out_file:
        data = response.read()
        out_file.write(data)
    print("Download complete.")
except Exception as e:
    print(f"Error downloading model: {e}")

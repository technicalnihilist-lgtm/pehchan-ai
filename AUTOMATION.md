# Scheduled update runbook

Every scheduled run ships **one small, verified improvement**. Never ship a build that fails
a check — a no-op report is better than a broken APK.

## 1. Get the code

```bash
git clone --depth 1 https://github.com/technicalnihilist-lgtm/pehchan-ai.git /scratch/work/repo
# if it already exists: git -C /scratch/work/repo pull --ff-only
```

## 2. Pick the work

Open `ROADMAP.md` and take the **first unchecked box**. If everything is ticked, add three new
worthwhile items to the roadmap and take the first one. Keep the change small enough to
implement, build and verify in one run.

## 3. Toolchain (the sandbox is fresh on every run)

```bash
mkdir -p /scratch/work/dl /scratch/work/sdk
curl -sL -o /scratch/work/dl/jdk.tar.gz "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.12_7.tar.gz"
curl -sL -o /scratch/work/dl/platform-35.zip "https://dl.google.com/android/repository/platform-35_r02.zip"
curl -sL -o /scratch/work/dl/buildtools-34.zip "https://dl.google.com/android/repository/build-tools_r34-linux.zip"
tar -xzf /scratch/work/dl/jdk.tar.gz -C /scratch/work/sdk && mv /scratch/work/sdk/jdk-17* /scratch/work/sdk/jdk
mkdir -p /scratch/work/sdk/platforms /scratch/work/sdk/build-tools
unzip -q /scratch/work/dl/platform-35.zip -d /tmp/pf && mv /tmp/pf/android-* /scratch/work/sdk/platforms/android-35
unzip -q /scratch/work/dl/buildtools-34.zip -d /tmp/bt && mv /tmp/bt/android-* /scratch/work/sdk/build-tools/34.0.0
```

Always `ulimit -v unlimited` before running java/d8 (the sandbox caps virtual memory).

**Models** — copy them in so nothing is downloaded:

```bash
cp /scratch/saved/model.tflite /scratch/saved/detect.tflite /scratch/work/repo/app/assets/
```

**Signing key** — copy the original key, or the update will not install over the current app:

```bash
cp /scratch/saved/pehchan-ai-signing-key.jks /scratch/work/repo/app/keystore.jks
```

The certificate SHA-256 must always be
`fdfdfda3eef778410b96b86eace568a554972c97152bacd6313c9d8131c58802`.

## 4. Build and verify

```bash
cd /scratch/work/repo
SDK=/scratch/work/sdk JAVA_HOME=/scratch/work/sdk/jdk bash app/build.sh
```

Checks that must pass before shipping:

* `apksigner verify --print-certs out/PehchanAI-vX.Y.apk` → digest matches the value above.
* `aapt2 dump badging` → `versionCode` is exactly one higher than the previous release.
* `unzip -l` → both `assets/model.tflite` and `assets/detect.tflite` are inside.
* If the UI changed: `python3 screenshots/render_shots.py` and eyeball the PNGs.

## 5. Ship

* Bump `versionCode`/`versionName` in `app/AndroidManifest.xml` **and** the version/URL
  constants in `app/build.sh`.
* Update `README.md`: changelog entry, download link, SHA-256.
* Tick the roadmap box.
* Commit through the **GitHub MCP** (`GITHUB_COMMIT_MULTIPLE_FILES`) — the `gh` CLI is
  read-only here. Text files commit fine; anything over ~3 MB (the APK, the models,
  screenshots >3 MB) must go to a release instead.
* Create the release (`GITHUB_CREATE_A_RELEASE`), then for each binary call the platform
  `upload` tool and pass its `s3key` to `GITHUB_UPLOAD_RELEASE_ASSET`.

## 6. Report

One short paragraph for the user: what changed, the new version, the APK link. If a check
fails, ship nothing and report exactly what failed.

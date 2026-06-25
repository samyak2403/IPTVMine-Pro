# F-Droid Submission Guide 🚀

This guide explains the step-by-step process to submit both **IPTVMine Pro** (mobile app) and **IPTVMine TV** (television app) to the official F-Droid repository.

---

## Step 1: Push a Release Tag to GitHub (CRITICAL)
F-Droid builds applications directly from tagged commits. Since we bumped the version code to `3` (versionName `1.0.3`), you must tag the current commit on the `master` branch and push it to GitHub.

Run the following commands in your terminal:
```bash
# Create the tag locally
git tag -a v1.0.3 -m "Release version 1.0.3 (versionCode 3)"

# Push the tag to your remote GitHub repository
git push origin v1.0.3
```

---

## Step 2: Set Up F-Droid Data Fork
1. Sign in to [GitLab](https://gitlab.com) (create an account if you don't have one).
2. Go to the official [F-Droid Metadata Repository](https://gitlab.com/fdroid/fdroiddata).
3. Click the **Fork** button in the top right to fork it to your GitLab account.
4. Clone your forked repository locally:
   ```bash
   git clone https://gitlab.com/YOUR_GITLAB_USERNAME/fdroiddata.git
   cd fdroiddata
   ```

---

## Step 3: Add Build Recipes to Your Fork
F-Droid uses YAML files in the `metadata/` folder of the `fdroiddata` repository to fetch, compile, and configure app listings.

We have generated these files for you in the `fastlane/` directory of your project:
1. Copy [com.samyak.iptvminepro.yml](file:///c:/Users/ADMIN/AndroidStudioProjects/IPTVMine-Pro/fastlane/com.samyak.iptvminepro.yml) into the `metadata/` directory of your `fdroiddata` clone.
2. Copy [com.samyak.television.yml](file:///c:/Users/ADMIN/AndroidStudioProjects/IPTVMine-Pro/fastlane/com.samyak.television.yml) into the `metadata/` directory of your `fdroiddata` clone.

---

## Step 4: Commit and Push the Recipes
Commit the two new recipe files to a new branch in your fork:
```bash
# Create a new branch
git checkout -b add-iptvmine-pro

# Stage the new recipe files
git add metadata/com.samyak.iptvminepro.yml metadata/com.samyak.television.yml

# Commit the recipes
git commit -m "Add IPTVMine Pro and IPTVMine TV"

# Push to your GitLab fork
git push origin add-iptvmine-pro
```

---

## Step 5: Open a Merge Request on GitLab
1. Open your fork page on GitLab: `https://gitlab.com/YOUR_GITLAB_USERNAME/fdroiddata`.
2. GitLab should display a banner suggesting to create a **Merge Request** (MR) from your newly pushed branch.
3. Click **Create Merge Request**.
4. Set the target repository to `fdroid/fdroiddata` (branch `master`).
5. Fill in the template:
   - Mark the checkboxes indicating the app is open-source (MIT License).
   - Ensure the description links back to your main repository `https://github.com/samyak2403/IPTVMine-Pro`.
6. Submit the Merge Request.

---

## How F-Droid Updates Your App Automatically
Thanks to the `AutoUpdateMode` and `UpdateCheckMode` configured in the recipes:
- F-Droid's bots run checkupdate checks periodically (usually every 24-48 hours).
- When you push a new tag matching `v*` (e.g. `v1.0.4`), the bot automatically detects it, updates the recipe file with the new version details, and triggers a build.
- Store descriptions and image assets are parsed automatically from the `fastlane/metadata/android/` folder in your GitHub repository, so you can update descriptions/screenshots without changing the F-Droid recipes!

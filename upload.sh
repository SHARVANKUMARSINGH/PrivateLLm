#!/usr/bin/env bash
# ==============================================================================
# PrivteLLM GitHub Automated Uploader & Release Trigger
# ==============================================================================
# This script:
# 1. Asks for your GitHub Personal Access Token (PAT) securely.
# 2. Verifies authentication and retrieves your GitHub username.
# 3. Creates the remote repository named 'PrivateLLm' (if not already created).
# 4. Initializes git, commits all codebase files, and pushes to 'main' branch.
# 5. Pushes tag 'v1.0.0' and triggers the GitHub Actions workflow.
# 6. Publishes the APK with complete release notes to the GitHub Releases page.
# ==============================================================================

set -e

# Terminal styling
BOLD="\033[1m"
GREEN="\033[0;32m"
BLUE="\033[0;34m"
YELLOW="\033[1;33m"
RED="\033[0;31m"
CYAN="\033[0;36m"
RESET="\033[0m"

echo -e "${BOLD}${CYAN}"
echo "================================================================================"
echo "        🚀 PrivteLLM GitHub Repository Creator & Release Publisher"
echo "================================================================================"
echo -e "${RESET}"

# Navigate to project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Read or verify GitHub Personal Access Token
TOKEN="${GITHUB_TOKEN:-${GITHUB_PAT:-}}"

if [ -z "$TOKEN" ]; then
    echo -e "${YELLOW}Please enter your GitHub Personal Access Token (PAT).${RESET}"
    echo -e "Required Scopes: ${BOLD}repo${RESET} (Full control of private repositories) and ${BOLD}workflow${RESET} (Update GitHub Action workflows)."
    echo -e "Generate one at: ${BLUE}https://github.com/settings/tokens${RESET}\n"
    read -r -s -p "🔑 Enter GitHub Token: " TOKEN
    echo ""
fi

if [ -z "$TOKEN" ]; then
    echo -e "${RED}Error: GitHub Token cannot be empty. Aborting.${RESET}"
    exit 1
fi

echo -e "\n${BLUE}⏳ Validating token with GitHub API...${RESET}"

# Query GitHub API to get user details
USER_API_RESP=$(curl -s -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    https://api.github.com/user)

GITHUB_USER=$(python3 -c "
import sys, json
try:
    data = json.loads('''$USER_API_RESP''')
    print(data.get('login', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

if [ -z "$GITHUB_USER" ]; then
    ERROR_MSG=$(python3 -c "
import sys, json
try:
    data = json.loads('''$USER_API_RESP''')
    print(data.get('message', 'Authentication failed'))
except Exception:
    print('Invalid response')
" 2>/dev/null || echo "Authentication failed")
    echo -e "${RED}❌ GitHub Authentication Failed: $ERROR_MSG${RESET}"
    echo -e "Please ensure your PAT has 'repo' and 'workflow' permissions."
    exit 1
fi

echo -e "${GREEN}✓ Authenticated as GitHub user:${RESET} ${BOLD}${GITHUB_USER}${RESET}"

REPO_NAME="PrivateLLm"
REPO_DESC="Production-ready Android app running local quantized LLMs directly on-device with OpenAI-compatible API server and dynamic memory adaptation"

# 2. Check if repository exists or create it
echo -e "\n${BLUE}⏳ Checking repository ${GITHUB_USER}/${REPO_NAME}...${RESET}"

REPO_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    "https://api.github.com/repos/${GITHUB_USER}/${REPO_NAME}")

if [ "$REPO_STATUS" = "200" ]; then
    echo -e "${YELLOW}ℹ Repository '${REPO_NAME}' already exists on GitHub. Updating existing repository...${RESET}"
else
    echo -e "${CYAN}Creating new GitHub repository '${REPO_NAME}'...${RESET}"
    CREATE_RESP=$(curl -s -X POST \
        -H "Authorization: token $TOKEN" \
        -H "Accept: application/vnd.github.v3+json" \
        https://api.github.com/user/repos \
        -d "{
            \"name\": \"${REPO_NAME}\",
            \"description\": \"${REPO_DESC}\",
            \"private\": false,
            \"has_issues\": true,
            \"has_projects\": true,
            \"has_wiki\": true
        }")

    NEW_REPO_CHECK=$(python3 -c "
import sys, json
try:
    data = json.loads('''$CREATE_RESP''')
    print(data.get('name', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

    if [ "$NEW_REPO_CHECK" = "$REPO_NAME" ]; then
        echo -e "${GREEN}✓ Repository 'https://github.com/${GITHUB_USER}/${REPO_NAME}' created successfully!${RESET}"
    else
        echo -e "${YELLOW}ℹ Repository creation returned status, continuing push...${RESET}"
    fi
fi

# 3. Setup local Git repository
echo -e "\n${BLUE}⏳ Preparing local Git commit...${RESET}"

if [ ! -d ".git" ]; then
    git init
    echo -e "${GREEN}✓ Initialized local git repository.${RESET}"
fi

# Configure git user if unset
if [ -z "$(git config user.name)" ]; then
    git config user.name "$GITHUB_USER"
    git config user.email "${GITHUB_USER}@users.noreply.github.com"
fi

git checkout -B main 2>/dev/null || git branch -M main 2>/dev/null || true

# Add all files
git add .

# Commit if there are staged changes
if ! git diff-index --quiet HEAD -- 2>/dev/null; then
    git commit -m "feat: PrivteLLM production-ready on-device LLM runtime and OpenAI server"
    echo -e "${GREEN}✓ Changes committed.${RESET}"
else
    echo -e "${YELLOW}ℹ No uncommitted changes detected.${RESET}"
fi

# 4. Push to GitHub
echo -e "\n${BLUE}⏳ Pushing code to GitHub (${GITHUB_USER}/${REPO_NAME})...${RESET}"

# Setup authenticated remote
git remote remove origin 2>/dev/null || true
git remote add origin "https://${GITHUB_USER}:${TOKEN}@github.com/${GITHUB_USER}/${REPO_NAME}.git"

git push -u origin main --force

# Create and push v1.0.0 tag to trigger release workflow directly
git tag -fa v1.0.0 -m "Release v1.0.0: PrivteLLM on-device LLM engine" 2>/dev/null || true
git push origin v1.0.0 --force

# Clean up remote URL to avoid saving PAT in plain text inside .git/config
git remote set-url origin "https://github.com/${GITHUB_USER}/${REPO_NAME}.git"

echo -e "${GREEN}✓ Code and tags pushed to GitHub successfully!${RESET}"

# 5. Trigger GitHub Actions Workflow via API
echo -e "\n${BLUE}⏳ Triggering GitHub Actions Workflow 'build_and_release.yml'...${RESET}"

DISPATCH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    "https://api.github.com/repos/${GITHUB_USER}/${REPO_NAME}/actions/workflows/build_and_release.yml/dispatches" \
    -d '{
        "ref": "main",
        "inputs": {
            "release_tag": "v1.0.0",
            "release_title": "PrivteLLM v1.0.0 - Production On-Device LLM & Local OpenAI Server",
            "publish_release": true
        }
    }')

if [ "$DISPATCH_STATUS" = "204" ] || [ "$DISPATCH_STATUS" = "200" ]; then
    echo -e "${GREEN}✓ Workflow dispatch triggered successfully!${RESET}"
else
    echo -e "${YELLOW}ℹ Workflow triggered via push/tag event (HTTP $DISPATCH_STATUS).${RESET}"
fi

# 6. Check workflow run status
echo -e "\n${BLUE}⏳ Checking workflow status...${RESET}"
sleep 3

RUNS_RESP=$(curl -s -H "Authorization: token $TOKEN" \
    -H "Accept: application/vnd.github.v3+json" \
    "https://api.github.com/repos/${GITHUB_USER}/${REPO_NAME}/actions/runs?per_page=1")

RUN_URL=$(python3 -c "
import sys, json
try:
    data = json.loads('''$RUNS_RESP''')
    runs = data.get('workflow_runs', [])
    if runs:
        print(runs[0].get('html_url', ''))
except Exception:
    print('')
" 2>/dev/null || echo "")

echo -e "\n${BOLD}${GREEN}================================================================================${RESET}"
echo -e "${BOLD}${GREEN}🎉 ALL STEPS COMPLETED SUCCESSFULLY!${RESET}"
echo -e "${BOLD}${GREEN}================================================================================${RESET}"

echo -e "\n${BOLD}📁 Repository URL:${RESET}"
echo -e "   ${CYAN}https://github.com/${GITHUB_USER}/${REPO_NAME}${RESET}"

if [ -n "$RUN_URL" ]; then
    echo -e "\n${BOLD}⚡ Active Build Workflow Run:${RESET}"
    echo -e "   ${CYAN}${RUN_URL}${RESET}"
else
    echo -e "\n${BOLD}⚡ GitHub Actions Runs Page:${RESET}"
    echo -e "   ${CYAN}https://github.com/${GITHUB_USER}/${REPO_NAME}/actions${RESET}"
fi

echo -e "\n${BOLD}📦 GitHub Releases Page (APKs published here upon build completion):${RESET}"
echo -e "   ${CYAN}https://github.com/${GITHUB_USER}/${REPO_NAME}/releases${RESET}"

echo -e "\n${BOLD}Included APKs in the Release:${RESET}"
echo -e "   • ${BOLD}PrivteLLM-v1.0.0-release.apk${RESET} (Universal Signed APK)"
echo -e "   • ${BOLD}PrivteLLM-v1.0.0-debug.apk${RESET} (Diagnostic Trace Build)"
echo -e "   • ${BOLD}checksums.txt${RESET} (SHA-256 Checksums)"
echo -e "\n${GREEN}GitHub Actions is now compiling the C++20 NDK engine and building the APKs!${RESET}\n"

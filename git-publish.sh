#!/usr/bin/env bash
# =============================================================================
# git-publish.sh — Init any folder and publish it to GitHub, no browser needed
#
# USAGE:
#   ./git-publish.sh [options]
#
# OPTIONS:
#   -t, --token   <token>       GitHub Personal Access Token (or set GH_TOKEN env var)
#   -u, --user    <username>    GitHub username              (or set GH_USER  env var)
#   -r, --repo    <name>        Repository name to create    (defaults to current folder name)
#   -d, --desc    <text>        Repository description       (optional)
#   -b, --branch  <name>        Default branch name          (default: main)
#       --private               Make the repository private  (default: public)
#       --dry-run               Print every step but do not execute
#   -h, --help                  Show this help message
#
# PREREQUISITES:
#   - git (any recent version)
#   - curl
#   - A GitHub Personal Access Token with the "repo" scope
#     Create one at: https://github.com/settings/tokens/new?scopes=repo
#
# EXAMPLES:
#   # Minimal — reads token + user from env vars, repo name from folder
#   export GH_TOKEN=ghp_xxxxxxxxxxxx
#   export GH_USER=your-github-username
#   ./git-publish.sh
#
#   # Fully explicit, private repo
#   ./git-publish.sh --token ghp_xxxx --user alice --repo my-project --private
#
#   # Custom branch + description, dry run first
#   ./git-publish.sh -t ghp_xxxx -u alice -r my-project -d "Resume tailor app" --dry-run
#   ./git-publish.sh -t ghp_xxxx -u alice -r my-project -d "Resume tailor app"
# =============================================================================

set -euo pipefail

# ─── Colour helpers ───────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}[ERROR]${RESET} $*" >&2; }
step()    { echo -e "\n${BOLD}▶ $*${RESET}"; }

# ─── Defaults ─────────────────────────────────────────────────────────────────
GH_TOKEN="${GH_TOKEN:-}"
GH_USER="${GH_USER:-}"
REPO_NAME=""          # resolved later from folder name if not supplied
REPO_DESC=""
BRANCH="main"
PRIVATE=false
DRY_RUN=false

# ─── Argument parsing ─────────────────────────────────────────────────────────
usage() {
  grep '^#' "$0" | grep -v '#!/' | sed 's/^# \{0,1\}//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -t|--token)   GH_TOKEN="$2";  shift 2 ;;
    -u|--user)    GH_USER="$2";   shift 2 ;;
    -r|--repo)    REPO_NAME="$2"; shift 2 ;;
    -d|--desc)    REPO_DESC="$2"; shift 2 ;;
    -b|--branch)  BRANCH="$2";    shift 2 ;;
    --private)    PRIVATE=true;   shift   ;;
    --dry-run)    DRY_RUN=true;   shift   ;;
    -h|--help)    usage ;;
    *) error "Unknown option: $1"; usage ;;
  esac
done

# ─── Dry-run wrapper ──────────────────────────────────────────────────────────
# Wraps any command: prints it in yellow, skips execution when --dry-run is set
run() {
  if [[ "$DRY_RUN" == true ]]; then
    echo -e "  ${YELLOW}[dry-run]${RESET} $*"
  else
    "$@"
  fi
}

# ─── Helper: GitHub API call ─────────────────────────────────────────────────
# gh_api <METHOD> <path> [curl-body-flags...]
# Always uses GH_TOKEN. Stores response body in GH_RESPONSE, status in GH_STATUS.
gh_api() {
  local method="$1"; local path="$2"; shift 2
  GH_STATUS="$(curl -s -o /tmp/gh_api_response.json -w "%{http_code}" \
    -X "$method" "https://api.github.com${path}" \
    -H "Authorization: token ${GH_TOKEN}" \
    -H "Accept: application/vnd.github+json" \
    -H "Content-Type: application/json" \
    "$@")"
  GH_RESPONSE="$(cat /tmp/gh_api_response.json)"
}

# ─── Pre-flight checks ────────────────────────────────────────────────────────
step "Pre-flight checks"

for cmd in git curl; do
  if ! command -v "$cmd" &>/dev/null; then
    error "'$cmd' is not installed. Please install it and retry."
    exit 1
  fi
done
success "git and curl are available"

[[ -z "$GH_TOKEN" ]] && { error "GitHub token is required. Use --token or export GH_TOKEN=..."; exit 1; }
[[ -z "$GH_USER"  ]] && { error "GitHub username is required. Use --user or export GH_USER=...";  exit 1; }

# ── Verify token + username against the GitHub API right now, before anything else
if [[ "$DRY_RUN" == false ]]; then
  info "Verifying token with GitHub API..."
  gh_api GET "/user"
  case "$GH_STATUS" in
    200)
      API_LOGIN="$(echo "$GH_RESPONSE" | grep '"login"' | head -1 | sed 's/.*"login": *"//;s/".*//')"
      if [[ "$API_LOGIN" != "$GH_USER" ]]; then
        warn "Token belongs to '${API_LOGIN}' but --user is '${GH_USER}'"
        warn "Using the token owner '${API_LOGIN}' as the GitHub user"
        GH_USER="$API_LOGIN"
      fi
      success "Token valid — authenticated as '${GH_USER}'"
      ;;
    401)
      error "Token is invalid or expired (HTTP 401)."
      error "Create a new one at: https://github.com/settings/tokens/new?scopes=repo"
      exit 1
      ;;
    403)
      error "Token does not have the required 'repo' scope (HTTP 403)."
      error "Create a new one at: https://github.com/settings/tokens/new?scopes=repo"
      exit 1
      ;;
    *)
      error "Unexpected response from GitHub API (HTTP ${GH_STATUS}):"
      echo "$GH_RESPONSE"
      exit 1
      ;;
  esac
fi

# Default repo name to the current directory name (strip path + spaces)
if [[ -z "$REPO_NAME" ]]; then
  REPO_NAME="$(basename "$(pwd)" | tr ' ' '-')"
  info "No repo name supplied — using folder name: '${REPO_NAME}'"
fi

VISIBILITY="public"
[[ "$PRIVATE" == true ]] && VISIBILITY="private"

success "Publishing '${REPO_NAME}' as a ${VISIBILITY} repo for user '${GH_USER}'"
[[ "$DRY_RUN" == true ]] && warn "DRY-RUN mode — no changes will be made"

# ─── Step 1: Initialise git repo ─────────────────────────────────────────────
step "Initialising local git repository"

if [[ -d ".git" ]]; then
  info ".git already exists — skipping init"
else
  run git init -b "$BRANCH"
  success "Initialised empty git repository"
fi

# Ensure the local default branch matches the requested branch.
# NOTE: We use `git symbolic-ref` instead of `git rev-parse --abbrev-ref`.
# On a brand-new empty repo (no commits yet), rev-parse returns the literal
# string "HEAD", making `git branch -m HEAD main` fail with
# "fatal: invalid branch name". symbolic-ref correctly returns the configured
# branch name (e.g. "master") even before the first commit exists.
CURRENT_BRANCH="$(git symbolic-ref --short HEAD 2>/dev/null || echo "")"
if [[ -n "$CURRENT_BRANCH" && "$CURRENT_BRANCH" != "$BRANCH" ]]; then
  warn "Current branch is '${CURRENT_BRANCH}', renaming to '${BRANCH}'"
  run git symbolic-ref HEAD "refs/heads/${BRANCH}"
  success "Branch renamed '${CURRENT_BRANCH}' -> '${BRANCH}'"
fi

# ─── Step 2: Stage and commit ─────────────────────────────────────────────────
step "Staging all files"

run git add --all

# Only commit if there is something to commit.
# Logic:
#   `git diff --cached --quiet`  true  = nothing staged
#   `git rev-parse HEAD`         exits 0 = at least one commit exists
# Guard: "nothing staged AND already committed" -> skip.
# Everything else (new files, or very first commit) -> commit.
if git diff --cached --quiet 2>/dev/null && git rev-parse HEAD &>/dev/null; then
  info "Nothing new to commit — working tree is clean"
else
  if git rev-parse HEAD &>/dev/null 2>&1; then
    COMMIT_MSG="chore: update via git-publish.sh"
  else
    COMMIT_MSG="chore: initial commit via git-publish.sh"
  fi
  run git commit -m "$COMMIT_MSG"
  success "Committed: '${COMMIT_MSG}'"
fi

# ─── Step 3: Create GitHub repository via API ────────────────────────────────
step "Creating GitHub repository via API"

REMOTE_URL="https://github.com/${GH_USER}/${REPO_NAME}.git"

# Check if the remote already exists
if git remote get-url origin &>/dev/null; then
  EXISTING_REMOTE="$(git remote get-url origin)"
  if [[ "$EXISTING_REMOTE" == "$REMOTE_URL" ]]; then
    info "Remote 'origin' already points to ${REMOTE_URL} — skipping repo creation"
    SKIP_CREATE=true
  else
    warn "Remote 'origin' exists but points to a different URL: ${EXISTING_REMOTE}"
    warn "Removing and resetting to ${REMOTE_URL}"
    run git remote remove origin
    SKIP_CREATE=false
  fi
else
  SKIP_CREATE=false
fi

if [[ "$SKIP_CREATE" == false ]]; then
  # Build JSON payload — use printf to safely handle special characters in desc
  JSON_PAYLOAD="$(printf '{"name":"%s","description":"%s","private":%s,"auto_init":false}' \
    "$REPO_NAME" "$REPO_DESC" "$PRIVATE")"

  if [[ "$DRY_RUN" == true ]]; then
    echo -e "  ${YELLOW}[dry-run]${RESET} POST https://api.github.com/user/repos"
    echo -e "            body: ${JSON_PAYLOAD}"
  else
    gh_api POST "/user/repos" -d "$JSON_PAYLOAD"

    case "$GH_STATUS" in
      201)
        success "Repository created on GitHub"
        ;;
      422)
        # 422 Unprocessable Entity means the repo already exists — that's fine
        warn "Repository already exists on GitHub — will push to it"
        ;;
      401|403)
        error "Authentication failed (HTTP ${GH_STATUS}). Token may be missing 'repo' scope."
        error "Create a new token: https://github.com/settings/tokens/new?scopes=repo"
        echo "$GH_RESPONSE"
        exit 1
        ;;
      *)
        error "Unexpected GitHub API response (HTTP ${GH_STATUS}):"
        echo "$GH_RESPONSE"
        exit 1
        ;;
    esac

    # ── Verify the repo is reachable via API before attempting a push.
    # GitHub occasionally takes a few seconds to make a freshly-created repo
    # available — pushing too early gives "repository not found".
    info "Verifying repository is reachable..."
    MAX_ATTEMPTS=6; SLEEP_SEC=3; ATTEMPT=0
    until gh_api GET "/repos/${GH_USER}/${REPO_NAME}" && [[ "$GH_STATUS" == "200" ]]; do
      ATTEMPT=$(( ATTEMPT + 1 ))
      if [[ $ATTEMPT -ge $MAX_ATTEMPTS ]]; then
        error "Repository '${GH_USER}/${REPO_NAME}' is not reachable after $((MAX_ATTEMPTS * SLEEP_SEC))s."
        error "Check that the repo was created at: https://github.com/${GH_USER}/${REPO_NAME}"
        exit 1
      fi
      warn "Not reachable yet (HTTP ${GH_STATUS}) — retrying in ${SLEEP_SEC}s... (${ATTEMPT}/${MAX_ATTEMPTS})"
      sleep "$SLEEP_SEC"
    done
    success "Repository confirmed reachable (HTTP 200)"
  fi

  # Add remote (clean URL — no token embedded in the stored config)
  run git remote add origin "$REMOTE_URL"
  success "Remote 'origin' → ${REMOTE_URL}"
fi

# ─── Step 4: Push ─────────────────────────────────────────────────────────────
step "Pushing to GitHub"

# Embed the token in the push URL for this call only so no password prompt
# appears. The stored remote (origin) stays as a clean https:// URL.
PUSH_URL="https://${GH_TOKEN}@github.com/${GH_USER}/${REPO_NAME}.git"

if [[ "$DRY_RUN" == true ]]; then
  echo -e "  ${YELLOW}[dry-run]${RESET} git push --set-upstream <token-url> ${BRANCH}"
else
  PUSH_OUTPUT="$(git push --set-upstream "$PUSH_URL" "$BRANCH" --force-with-lease 2>&1)" && PUSH_EXIT=0 || PUSH_EXIT=$?
  echo "$PUSH_OUTPUT"

  if [[ $PUSH_EXIT -ne 0 ]]; then
    # ── Detect branch protection / ruleset violations and handle gracefully
    if echo "$PUSH_OUTPUT" | grep -qi "rule violations\|protected branch\|required review\|cannot push"; then
      echo ""
      warn "Direct push to '${BRANCH}' was rejected by a GitHub branch ruleset."
      warn "Falling back: pushing to a staging branch and opening a Pull Request instead..."

      # ── Push to a staging branch
      STAGING_BRANCH="publish/$(date +%Y%m%d-%H%M%S)"
      info "Creating staging branch '${STAGING_BRANCH}'..."
      git checkout -b "$STAGING_BRANCH" 2>/dev/null || git switch -c "$STAGING_BRANCH"
      git push --set-upstream "$PUSH_URL" "$STAGING_BRANCH"
      success "Pushed to staging branch '${STAGING_BRANCH}'"

      # ── Open a Pull Request via the GitHub API
      info "Opening Pull Request: '${STAGING_BRANCH}' → '${BRANCH}'..."
      PR_PAYLOAD="$(printf \
        '{"title":"chore: publish via git-publish.sh","head":"%s","base":"%s","body":"Automated PR created by git-publish.sh. Merge to publish the initial code to %s."}' \
        "$STAGING_BRANCH" "$BRANCH" "$BRANCH")"

      gh_api POST "/repos/${GH_USER}/${REPO_NAME}/pulls" -d "$PR_PAYLOAD"

      case "$GH_STATUS" in
        201)
          PR_URL="$(echo "$GH_RESPONSE" | grep '"html_url"' | head -1 | sed 's/.*"html_url": *"//;s/".*//')"
          success "Pull Request opened!"
          echo ""
          echo -e "${GREEN}${BOLD}✔ All done!${RESET}"
          echo -e "  ${BOLD}Repo URL :${RESET} https://github.com/${GH_USER}/${REPO_NAME}"
          echo -e "  ${BOLD}PR URL   :${RESET} ${PR_URL}"
          echo -e "  ${BOLD}Branch   :${RESET} ${STAGING_BRANCH} → ${BRANCH}"
          echo ""
          echo -e "  ${YELLOW}Merge the PR to publish your code to '${BRANCH}'.${RESET}"
          echo -e "  ${YELLOW}Or, if you own the ruleset, disable it, merge, then re-enable it.${RESET}"
          echo ""
          exit 0
          ;;
        422)
          warn "A PR from '${STAGING_BRANCH}' to '${BRANCH}' already exists."
          echo -e "  Open PRs: https://github.com/${GH_USER}/${REPO_NAME}/pulls"
          exit 0
          ;;
        *)
          error "Could not open Pull Request (HTTP ${GH_STATUS}):"
          echo "$GH_RESPONSE"
          error "Your code IS pushed to branch '${STAGING_BRANCH}'."
          error "Open a PR manually: https://github.com/${GH_USER}/${REPO_NAME}/compare/${STAGING_BRANCH}"
          exit 1
          ;;
      esac
    else
      # ── Some other push failure — surface the raw error
      echo ""
      error "Push failed. Raw git output is above. Common causes:"
      error "  1. Token is missing 'repo' scope  → https://github.com/settings/tokens/new?scopes=repo"
      error "  2. Remote has commits not in your local history"
      exit 1
    fi
  fi

  success "Pushed '${BRANCH}' to ${REMOTE_URL}"
fi

# ─── Done ─────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}${BOLD}✔ All done!${RESET}"
echo -e "  ${BOLD}Repo URL :${RESET} https://github.com/${GH_USER}/${REPO_NAME}"
echo -e "  ${BOLD}Clone    :${RESET} git clone ${REMOTE_URL}"
[[ "$DRY_RUN" == true ]] && echo -e "\n  ${YELLOW}This was a dry-run. Re-run without --dry-run to apply changes.${RESET}"
echo ""

#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Pirate banner
echo ""
echo -e "${CYAN}⚓️ ════════════════════════════════════════════════════════════ ⚓️${NC}"
echo -e "${YELLOW}"
cat << 'EOF'
    ____        __        __
   / __ \____ _/ /_____ _/ /____  ____ _
  / / / / __ `/ __/ __ `/ / __ \/ __ `/
 / /_/ / /_/ / /_/ /_/ / / /_/ / /_/ /
/_____/\__,_/\__/\__,_/_/\____/\__, /
                              /____/
   __  __________  _____
  /  |/  / ____/ |/ /  /_________  _________ _____
 / /|_/ / /   /    / __/ ___/ __ \/ ___/ __ `/ __ \
/ /  / / /___/ /| / /_/ /  / /_/ / /__/ /_/ / /_/ /
/_/  /_/\____/_/ |_\__/_/   \____/\___/\__,_/ .___/
                                          /_/
EOF
echo -e "${NC}"
echo -e "${CYAN}⚓️ ════════════════════════════════════════════════════════════ ⚓️${NC}"
echo ""
echo -e "${GREEN}Ahoy there, matey! 🏴‍☠️${NC}"
echo ""
echo -e "I be ${YELLOW}waabox${NC}, yer humble captain on this here voyage!"
echo -e "I'll be helpin' ye discover the ${RED}troubles${NC} lurkin' in yer production"
echo -e "waters, usin' the mighty ${BLUE}Datadog${NC} treasure maps! 🗺️"
echo ""
echo -e "With this here MCP server, ye can ask ${CYAN}Claude${NC} to hunt down"
echo -e "them pesky error traces and chart a course to fix 'em! ⚔️"
echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Detect OS
OS="$(uname -s)"
case "${OS}" in
    Linux*)     MACHINE=Linux;;
    Darwin*)    MACHINE=Mac;;
    CYGWIN*|MINGW*|MSYS*) MACHINE=Windows;;
    *)          MACHINE="UNKNOWN:${OS}"
esac

# Set paths based on OS
if [ "$MACHINE" = "Mac" ] || [ "$MACHINE" = "Linux" ]; then
    CLAUDE_CONFIG_DIR="$HOME/.claude"
    MCP_APPS_DIR="$HOME/.claude/apps/mcp"
    MCP_CONFIG_FILE="$CLAUDE_CONFIG_DIR/mcp.json"
elif [ "$MACHINE" = "Windows" ]; then
    CLAUDE_CONFIG_DIR="$APPDATA/claude"
    MCP_APPS_DIR="$APPDATA/claude/apps/mcp"
    MCP_CONFIG_FILE="$CLAUDE_CONFIG_DIR/mcp.json"
else
    echo -e "${RED}Blimey! I don't recognize this operating system: $MACHINE${NC}"
    echo "This installer only works on Mac, Linux, or Windows (Git Bash/WSL)"
    exit 1
fi

JAR_NAME="datadog-mcp-server-1.0.0-SNAPSHOT.jar"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Check if JAR exists in current directory or target/
if [ -f "$SCRIPT_DIR/$JAR_NAME" ]; then
    JAR_SOURCE="$SCRIPT_DIR/$JAR_NAME"
elif [ -f "$SCRIPT_DIR/target/$JAR_NAME" ]; then
    JAR_SOURCE="$SCRIPT_DIR/target/$JAR_NAME"
else
    echo -e "${RED}Arr! I can't find the treasure (JAR file)!${NC}"
    echo ""
    echo "Make sure ye have either:"
    echo "  - $JAR_NAME in the current directory"
    echo "  - Or run 'mvn package' to build it first"
    echo ""
    exit 1
fi

echo -e "🔍 Found the treasure chest at: ${GREEN}$JAR_SOURCE${NC}"
echo ""

# Create directories
echo -e "📁 Creatin' the secret hideout at ${CYAN}$MCP_APPS_DIR${NC}..."
mkdir -p "$MCP_APPS_DIR"

# Copy JAR
echo -e "📦 Buryin' the treasure (copyin' JAR)..."
cp "$JAR_SOURCE" "$MCP_APPS_DIR/$JAR_NAME"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ JAR successfully copied to $MCP_APPS_DIR/$JAR_NAME${NC}"
else
    echo -e "${RED}✗ Failed to copy JAR file${NC}"
    exit 1
fi

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Ask for Datadog credentials
echo -e "🔑 Now I need yer ${YELLOW}Datadog API credentials${NC} to unlock the treasure!"
echo ""
echo -e "   Ye can get these from yer Datadog account:"
echo -e "   ${BLUE}Organization Settings → API Keys / Application Keys${NC}"
echo ""

read -p "$(echo -e ${YELLOW}"Do ye want to enter yer API keys now? (y/n): "${NC})" ENTER_KEYS

DATADOG_API_KEY=""
DATADOG_APP_KEY=""
DATADOG_SITE="datadoghq.com"

if [[ "$ENTER_KEYS" =~ ^[Yy]$ ]]; then
    echo ""
    read -p "$(echo -e ${CYAN}"Enter yer DATADOG_API_KEY: "${NC})" DATADOG_API_KEY
    read -p "$(echo -e ${CYAN}"Enter yer DATADOG_APP_KEY: "${NC})" DATADOG_APP_KEY

    echo ""
    echo -e "Which Datadog site are ye sailin' from?"
    echo "  1) datadoghq.com (US1 - default)"
    echo "  2) us3.datadoghq.com (US3)"
    echo "  3) us5.datadoghq.com (US5)"
    echo "  4) datadoghq.eu (EU)"
    echo "  5) ap1.datadoghq.com (AP1)"
    read -p "$(echo -e ${CYAN}"Choose yer port [1-5] (default: 1): "${NC})" SITE_CHOICE

    case "$SITE_CHOICE" in
        2) DATADOG_SITE="us3.datadoghq.com";;
        3) DATADOG_SITE="us5.datadoghq.com";;
        4) DATADOG_SITE="datadoghq.eu";;
        5) DATADOG_SITE="ap1.datadoghq.com";;
        *) DATADOG_SITE="datadoghq.com";;
    esac
else
    echo ""
    echo -e "${YELLOW}⚠️  No worries, matey! Ye can add yer keys later.${NC}"
    echo -e "   Just edit this file: ${CYAN}$MCP_CONFIG_FILE${NC}"
    DATADOG_API_KEY="YOUR_API_KEY_HERE"
    DATADOG_APP_KEY="YOUR_APP_KEY_HERE"
fi

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""

# Create or update MCP config
JAR_PATH="$MCP_APPS_DIR/$JAR_NAME"

echo -e "⚙️  Configurin' Claude Code to use the Datadog MCP server..."

# Check if mcp.json exists
if [ -f "$MCP_CONFIG_FILE" ]; then
    echo -e "${YELLOW}Found existing mcp.json, backing it up...${NC}"
    cp "$MCP_CONFIG_FILE" "$MCP_CONFIG_FILE.backup.$(date +%Y%m%d%H%M%S)"

    # Check if jq is available for JSON manipulation
    if command -v jq &> /dev/null; then
        # Use jq to add/update the datadog-traces server
        TEMP_FILE=$(mktemp)
        jq --arg jar "$JAR_PATH" \
           --arg api_key "$DATADOG_API_KEY" \
           --arg app_key "$DATADOG_APP_KEY" \
           --arg site "$DATADOG_SITE" \
           '.mcpServers["datadog-traces"] = {
               "command": "java",
               "args": ["-jar", $jar],
               "env": {
                   "DATADOG_API_KEY": $api_key,
                   "DATADOG_APP_KEY": $app_key,
                   "DATADOG_SITE": $site
               }
           }' "$MCP_CONFIG_FILE" > "$TEMP_FILE" && mv "$TEMP_FILE" "$MCP_CONFIG_FILE"
        echo -e "${GREEN}✓ Updated existing mcp.json with datadog-traces server${NC}"
    else
        echo -e "${YELLOW}⚠️  jq not found, creating new config (old one backed up)${NC}"
        # Create new config
        cat > "$MCP_CONFIG_FILE" << EOF
{
  "mcpServers": {
    "datadog-traces": {
      "command": "java",
      "args": ["-jar", "$JAR_PATH"],
      "env": {
        "DATADOG_API_KEY": "$DATADOG_API_KEY",
        "DATADOG_APP_KEY": "$DATADOG_APP_KEY",
        "DATADOG_SITE": "$DATADOG_SITE"
      }
    }
  }
}
EOF
    fi
else
    # Create new config file
    cat > "$MCP_CONFIG_FILE" << EOF
{
  "mcpServers": {
    "datadog-traces": {
      "command": "java",
      "args": ["-jar", "$JAR_PATH"],
      "env": {
        "DATADOG_API_KEY": "$DATADOG_API_KEY",
        "DATADOG_APP_KEY": "$DATADOG_APP_KEY",
        "DATADOG_SITE": "$DATADOG_SITE"
      }
    }
  }
}
EOF
    echo -e "${GREEN}✓ Created new mcp.json configuration${NC}"
fi

echo ""
echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo ""
echo -e "${GREEN}🎉 YARR! Installation complete, Captain! 🏴‍☠️${NC}"
echo ""
echo -e "╭────────────────────────────────────────────────────────────────╮"
echo -e "│                                                                │"
echo -e "│  ${CYAN}JAR Location:${NC}    $JAR_PATH"
echo -e "│  ${CYAN}Config File:${NC}     $MCP_CONFIG_FILE"
echo -e "│                                                                │"
echo -e "╰────────────────────────────────────────────────────────────────╯"
echo ""

if [[ "$DATADOG_API_KEY" == "YOUR_API_KEY_HERE" ]]; then
    echo -e "${YELLOW}⚠️  IMPORTANT: Don't forget to add yer Datadog keys!${NC}"
    echo -e "   Edit: ${CYAN}$MCP_CONFIG_FILE${NC}"
    echo ""
fi

echo -e "🚀 ${GREEN}Now restart Claude Code and try askin':${NC}"
echo ""
echo -e "   ${CYAN}\"List error traces for my-service from the last hour\"${NC}"
echo ""
echo -e "   ${CYAN}\"Analyze trace abc123 and help me debug it\"${NC}"
echo ""
echo -e "${YELLOW}Fair winds and following seas, matey! ⚓️${NC}"
echo ""

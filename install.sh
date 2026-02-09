#!/bin/bash

# Datadog MCP Server Installer
# Usage: curl -fsSL https://raw.githubusercontent.com/waabox/datadog-mcp-server/main/install.sh | bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
REPO_URL="https://github.com/waabox/datadog-mcp-server"
STABLE_TAG="v1.4.0"
JAR_NAME="datadog-mcp-server-1.4.0.jar"
JAR_DOWNLOAD_URL="$REPO_URL/releases/download/$STABLE_TAG/$JAR_NAME"

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

# Check requirements
check_requirements() {
    echo -e "🔍 Checkin' if ye have the proper tools aboard..."
    echo ""

    # Check for Java 21+
    if ! command -v java &> /dev/null; then
        echo -e "${RED}✗ Blimey! Java be missing from yer ship!${NC}"
        echo "  Install Java 21 or higher and try again."
        echo "  https://adoptium.net/temurin/releases/"
        exit 1
    fi

    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
        echo -e "${RED}✗ Arr! Yer Java be too old (version $JAVA_VERSION)!${NC}"
        echo "  This treasure requires Java 21 or higher."
        exit 1
    fi
    echo -e "${GREEN}✓ Java $JAVA_VERSION found${NC}"

    # Check for curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}✗ Curl be missing from yer arsenal!${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ curl found${NC}"

    echo ""
}

# Detect OS and set paths
setup_paths() {
    OS="$(uname -s)"
    case "${OS}" in
        Linux*)     MACHINE=Linux;;
        Darwin*)    MACHINE=Mac;;
        CYGWIN*|MINGW*|MSYS*) MACHINE=Windows;;
        *)          MACHINE="UNKNOWN:${OS}"
    esac

    if [ "$MACHINE" = "Mac" ] || [ "$MACHINE" = "Linux" ]; then
        CLAUDE_CONFIG_DIR="$HOME/.claude"
        MCP_APPS_DIR="$HOME/.claude/apps/mcp"
        MCP_CONFIG_FILE="$HOME/.claude.json"
    elif [ "$MACHINE" = "Windows" ]; then
        CLAUDE_CONFIG_DIR="$APPDATA/claude"
        MCP_APPS_DIR="$APPDATA/claude/apps/mcp"
        MCP_CONFIG_FILE="$APPDATA/.claude.json"
    else
        echo -e "${RED}Blimey! I don't recognize this operating system: $MACHINE${NC}"
        exit 1
    fi
}

# Check if waabox-datadog-mcp is already configured in mcp.json
check_existing_config() {
    ALREADY_CONFIGURED=false

    if [ -f "$MCP_CONFIG_FILE" ]; then
        if command -v jq &> /dev/null; then
            # Use jq to check if waabox-datadog-mcp exists and has valid API key
            if jq -e '.mcpServers["waabox-datadog-mcp"]' "$MCP_CONFIG_FILE" &> /dev/null; then
                API_KEY=$(jq -r '.mcpServers["waabox-datadog-mcp"].env.DATADOG_API_KEY // ""' "$MCP_CONFIG_FILE")
                if [ -n "$API_KEY" ] && [ "$API_KEY" != "YOUR_API_KEY_HERE" ]; then
                    ALREADY_CONFIGURED=true
                fi
            fi
        else
            # Fallback: simple grep check
            if grep -q '"waabox-datadog-mcp"' "$MCP_CONFIG_FILE" 2>/dev/null; then
                if ! grep -q 'YOUR_API_KEY_HERE' "$MCP_CONFIG_FILE" 2>/dev/null; then
                    ALREADY_CONFIGURED=true
                fi
            fi
        fi
    fi

    if [ "$ALREADY_CONFIGURED" = true ]; then
        echo ""
        echo -e "🔍 ${GREEN}Found existing waabox-datadog-mcp configuration!${NC}"
        echo -e "   Config file: ${CYAN}$MCP_CONFIG_FILE${NC}"
        echo ""
        echo -e "   ${YELLOW}Upgrade mode:${NC} Will only download and update the JAR file."
        echo -e "   Your existing credentials and settings will be preserved."
        echo ""
    fi
}

# Download JAR from GitHub release
download_jar() {
    echo -e "📥 Downloadin' the treasure from GitHub release ${YELLOW}$STABLE_TAG${NC}..."
    echo -e "   ${CYAN}$JAR_DOWNLOAD_URL${NC}"
    echo ""

    # Create destination directory
    mkdir -p "$MCP_APPS_DIR"

    # Clean up old versions of the JAR
    cleanup_old_versions

    # Download JAR directly to destination
    if curl -fsSL "$JAR_DOWNLOAD_URL" -o "$MCP_APPS_DIR/$JAR_NAME"; then
        echo -e "${GREEN}✓ Downloaded $JAR_NAME${NC}"
    else
        echo -e "${RED}✗ Failed to download JAR from GitHub release${NC}"
        echo -e "   URL: $JAR_DOWNLOAD_URL"
        exit 1
    fi

    echo ""
}

# Clean up old versions of the JAR
cleanup_old_versions() {
    # Find and remove old datadog-mcp-server-*.jar files (but not the current version)
    OLD_JARS=$(find "$MCP_APPS_DIR" -maxdepth 1 -name "datadog-mcp-server-*.jar" ! -name "$JAR_NAME" 2>/dev/null)

    if [ -n "$OLD_JARS" ]; then
        echo -e "🧹 Cleanin' up old versions of the treasure..."
        while IFS= read -r old_jar; do
            if [ -f "$old_jar" ]; then
                rm -f "$old_jar"
                echo -e "   ${YELLOW}✓ Removed: $(basename "$old_jar")${NC}"
            fi
        done <<< "$OLD_JARS"
        echo ""
    fi
}

# Get Datadog credentials
get_credentials() {
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo -e "🔑 Now I need yer ${YELLOW}Datadog API credentials${NC} to unlock the treasure!"
    echo ""
    echo -e "   Ye can get these from yer Datadog account:"
    echo -e "   ${BLUE}Organization Settings → API Keys / Application Keys${NC}"
    echo ""
    echo -e "   Required scopes for Application Key:"
    echo -e "   • ${CYAN}apm_read${NC} - Read APM data"
    echo -e "   • ${CYAN}logs_read_data${NC} - Read logs data"
    echo ""

    DATADOG_API_KEY=""
    DATADOG_APP_KEY=""
    DATADOG_SITE="datadoghq.com"

    # Check if running interactively (TTY available on stdin)
    if [ -t 0 ]; then
        # Interactive mode - prompt for input
        echo -e -n "${YELLOW}Do ye want to enter yer API keys now? (y/n): ${NC}"
        read ENTER_KEYS

        if [[ "$ENTER_KEYS" =~ ^[Yy]$ ]]; then
            echo ""
            echo -e -n "${CYAN}Enter yer DATADOG_API_KEY: ${NC}"
            read DATADOG_API_KEY
            echo -e -n "${CYAN}Enter yer DATADOG_APP_KEY: ${NC}"
            read DATADOG_APP_KEY

            echo ""
            echo -e "Which Datadog site are ye sailin' from?"
            echo "  1) datadoghq.com (US1 - default)"
            echo "  2) us3.datadoghq.com (US3)"
            echo "  3) us5.datadoghq.com (US5)"
            echo "  4) datadoghq.eu (EU)"
            echo "  5) ap1.datadoghq.com (AP1)"
            echo -e -n "${CYAN}Choose yer port [1-5] (default: 1): ${NC}"
            read SITE_CHOICE

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
            echo -e "   Just edit the mcpServers section in: ${CYAN}$MCP_CONFIG_FILE${NC}"
            DATADOG_API_KEY="YOUR_API_KEY_HERE"
            DATADOG_APP_KEY="YOUR_APP_KEY_HERE"
        fi
    else
        # Non-interactive mode (piped from curl)
        echo -e "${YELLOW}⚠️  Running in non-interactive mode (piped install).${NC}"
        echo ""
        echo -e "   To enter yer keys interactively, run the installer like this:"
        echo -e "   ${CYAN}curl -fsSL https://raw.githubusercontent.com/waabox/datadog-mcp-server/main/install.sh -o install.sh${NC}"
        echo -e "   ${CYAN}chmod +x install.sh && ./install.sh${NC}"
        echo ""
        echo -e "   Or edit the mcpServers section after installation:"
        echo -e "   ${CYAN}$MCP_CONFIG_FILE${NC}"
        DATADOG_API_KEY="YOUR_API_KEY_HERE"
        DATADOG_APP_KEY="YOUR_APP_KEY_HERE"
    fi
    echo ""
}

# Configure Claude Code
configure_claude() {
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo -e "⚙️  Configurin' Claude Code to use the Datadog MCP server..."

    JAR_PATH="$MCP_APPS_DIR/$JAR_NAME"

    # Ensure config directory exists
    mkdir -p "$CLAUDE_CONFIG_DIR"

    if [ -f "$MCP_CONFIG_FILE" ]; then
        echo -e "${YELLOW}Found existing mcp.json...${NC}"

        if command -v jq &> /dev/null; then
            # Use jq to merge - preserves all existing settings and other MCP servers
            TEMP_FILE=$(mktemp)
            # First ensure mcpServers key exists, then add/update waabox-datadog-mcp
            jq --arg jar "$JAR_PATH" \
               --arg api_key "$DATADOG_API_KEY" \
               --arg app_key "$DATADOG_APP_KEY" \
               --arg site "$DATADOG_SITE" \
               '.mcpServers = (.mcpServers // {}) | .mcpServers["waabox-datadog-mcp"] = {
                   "command": "java",
                   "args": ["--enable-preview", "-jar", $jar],
                   "env": {
                       "DATADOG_API_KEY": $api_key,
                       "DATADOG_APP_KEY": $app_key,
                       "DATADOG_SITE": $site
                   }
               }' "$MCP_CONFIG_FILE" > "$TEMP_FILE" && mv "$TEMP_FILE" "$MCP_CONFIG_FILE"
            echo -e "${GREEN}✓ Updated waabox-datadog-mcp in mcp.json (all other MCP servers preserved)${NC}"
        else
            # No jq - need to be careful not to overwrite other settings
            echo -e "${YELLOW}⚠️  jq not found. Cannot safely merge config.${NC}"
            echo ""
            echo -e "   Please add this to the ${CYAN}mcpServers${NC} section in ${CYAN}$MCP_CONFIG_FILE${NC}:"
            echo ""
            echo -e "${BLUE}   \"waabox-datadog-mcp\": {"
            echo -e "     \"command\": \"java\","
            echo -e "     \"args\": [\"--enable-preview\", \"-jar\", \"$JAR_PATH\"],"
            echo -e "     \"env\": {"
            echo -e "       \"DATADOG_API_KEY\": \"$DATADOG_API_KEY\","
            echo -e "       \"DATADOG_APP_KEY\": \"$DATADOG_APP_KEY\","
            echo -e "       \"DATADOG_SITE\": \"$DATADOG_SITE\""
            echo -e "     }"
            echo -e "   }${NC}"
            echo ""
            echo -e "   Or install jq: ${CYAN}brew install jq${NC} (Mac) / ${CYAN}apt install jq${NC} (Linux)"
        fi
    else
        # No existing config - create new mcp.json with mcpServers
        write_new_config
    fi
    echo ""
}

write_new_config() {
    JAR_PATH="$MCP_APPS_DIR/$JAR_NAME"
    cat > "$MCP_CONFIG_FILE" << EOF
{
  "mcpServers": {
    "waabox-datadog-mcp": {
      "command": "java",
      "args": ["--enable-preview", "-jar", "$JAR_PATH"],
      "env": {
        "DATADOG_API_KEY": "$DATADOG_API_KEY",
        "DATADOG_APP_KEY": "$DATADOG_APP_KEY",
        "DATADOG_SITE": "$DATADOG_SITE"
      }
    }
  }
}
EOF
    echo -e "${GREEN}✓ Created mcp.json configuration${NC}"
}

# Show completion message
show_completion() {
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""

    if [ "$ALREADY_CONFIGURED" = true ]; then
        echo -e "${GREEN}🎉 YARR! Upgrade complete, Captain! 🏴‍☠️${NC}"
    else
        echo -e "${GREEN}🎉 YARR! Installation complete, Captain! 🏴‍☠️${NC}"
    fi

    echo ""
    echo -e "╭────────────────────────────────────────────────────────────────╮"
    echo -e "│                                                                │"
    echo -e "│  ${CYAN}JAR Location:${NC}    $MCP_APPS_DIR/$JAR_NAME"
    echo -e "│  ${CYAN}Config File:${NC}     $MCP_CONFIG_FILE"
    echo -e "│  ${CYAN}Data Directory:${NC}  ~/.claude/mcp/waabox-mcp-server/"
    echo -e "│  ${CYAN}Version:${NC}         $STABLE_TAG"
    echo -e "│                                                                │"
    echo -e "╰────────────────────────────────────────────────────────────────╯"
    echo ""

    if [ "$ALREADY_CONFIGURED" = true ]; then
        echo -e "${GREEN}✓ Your existing configuration was preserved.${NC}"
        echo ""
    elif [[ "$DATADOG_API_KEY" == "YOUR_API_KEY_HERE" ]]; then
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
    echo -e "   ${CYAN}\"Search logs for my-service with level ERROR\"${NC}"
    echo ""
    echo -e "${YELLOW}Fair winds and following seas, matey! ⚓️${NC}"
    echo ""
}

# Main execution
main() {
    check_requirements
    setup_paths
    check_existing_config
    download_jar

    if [ "$ALREADY_CONFIGURED" = true ]; then
        # Skip credentials and config - just update JAR
        echo -e "${GREEN}✓ JAR updated successfully!${NC}"
    else
        # First time install - get credentials and configure
        get_credentials
        configure_claude
    fi

    show_completion
}

# Run main
main

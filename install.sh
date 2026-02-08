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
STABLE_TAG="v1.0.0"
JAR_NAME="datadog-mcp-server-1.0.0.jar"

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

    # Check for Maven
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}✗ Shiver me timbers! Maven be missing!${NC}"
        echo "  Install Maven 3.8+ and try again."
        echo "  https://maven.apache.org/install.html"
        exit 1
    fi
    echo -e "${GREEN}✓ Maven found${NC}"

    # Check for curl
    if ! command -v curl &> /dev/null; then
        echo -e "${RED}✗ Curl be missing from yer arsenal!${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ curl found${NC}"

    # Check for git or tar
    if ! command -v git &> /dev/null && ! command -v tar &> /dev/null; then
        echo -e "${RED}✗ Neither git nor tar be available!${NC}"
        exit 1
    fi

    if command -v git &> /dev/null; then
        echo -e "${GREEN}✓ git found${NC}"
    else
        echo -e "${GREEN}✓ tar found (will download archive)${NC}"
    fi

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
        TEMP_BUILD_DIR="/tmp/datadog-mcp-build-$$"
    elif [ "$MACHINE" = "Windows" ]; then
        CLAUDE_CONFIG_DIR="$APPDATA/claude"
        MCP_APPS_DIR="$APPDATA/claude/apps/mcp"
        MCP_CONFIG_FILE="$APPDATA/.claude.json"
        TEMP_BUILD_DIR="/tmp/datadog-mcp-build-$$"
    else
        echo -e "${RED}Blimey! I don't recognize this operating system: $MACHINE${NC}"
        exit 1
    fi
}

# Download and build
download_and_build() {
    echo -e "📥 Downloadin' the treasure from ${CYAN}$REPO_URL${NC} (tag: ${YELLOW}$STABLE_TAG${NC})..."
    echo ""

    # Create temp directory in /tmp
    mkdir -p "$TEMP_BUILD_DIR"
    cd "$TEMP_BUILD_DIR"

    if command -v git &> /dev/null; then
        # Use git clone with specific tag
        git clone --depth 1 --branch "$STABLE_TAG" "$REPO_URL.git" datadog-mcp-server 2>&1 | while read line; do
            echo -e "   ${BLUE}git:${NC} $line"
        done
        cd datadog-mcp-server
    else
        # Fallback to downloading tarball
        echo -e "   Downloadin' archive..."
        curl -fsSL "$REPO_URL/archive/refs/tags/$STABLE_TAG.tar.gz" -o source.tar.gz
        tar -xzf source.tar.gz
        cd datadog-mcp-server-*
    fi

    echo ""
    echo -e "🔨 Buildin' the treasure (this might take a minute)..."
    echo ""

    # Build without tests
    mvn clean package -DskipTests -q

    if [ ! -f "target/$JAR_NAME" ]; then
        echo -e "${RED}✗ Build failed! The JAR file was not created.${NC}"
        exit 1
    fi

    echo -e "${GREEN}✓ Build successful!${NC}"
    echo ""

    # Set JAR source path
    JAR_SOURCE="$(pwd)/target/$JAR_NAME"
}

# Install JAR
install_jar() {
    echo -e "📁 Creatin' the hideout at ${CYAN}$MCP_APPS_DIR${NC}..."
    mkdir -p "$MCP_APPS_DIR"

    echo -e "📦 Copyin' the treasure to its final restin' place..."
    cp "$JAR_SOURCE" "$MCP_APPS_DIR/$JAR_NAME"

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ JAR installed at $MCP_APPS_DIR/$JAR_NAME${NC}"
    else
        echo -e "${RED}✗ Failed to copy JAR file${NC}"
        exit 1
    fi
    echo ""
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
        echo -e "${YELLOW}Found existing .claude.json...${NC}"

        if command -v jq &> /dev/null; then
            # Use jq to merge - preserves all existing settings and other MCP servers
            TEMP_FILE=$(mktemp)
            # First ensure mcpServers key exists, then add/update datadog-traces
            jq --arg jar "$JAR_PATH" \
               --arg api_key "$DATADOG_API_KEY" \
               --arg app_key "$DATADOG_APP_KEY" \
               --arg site "$DATADOG_SITE" \
               '.mcpServers = (.mcpServers // {}) | .mcpServers["datadog-traces"] = {
                   "command": "java",
                   "args": ["-jar", $jar],
                   "env": {
                       "DATADOG_API_KEY": $api_key,
                       "DATADOG_APP_KEY": $app_key,
                       "DATADOG_SITE": $site
                   }
               }' "$MCP_CONFIG_FILE" > "$TEMP_FILE" && mv "$TEMP_FILE" "$MCP_CONFIG_FILE"
            echo -e "${GREEN}✓ Updated datadog-traces in .claude.json (all other settings preserved)${NC}"
        else
            # No jq - need to be careful not to overwrite other settings
            echo -e "${YELLOW}⚠️  jq not found. Cannot safely merge config.${NC}"
            echo ""
            echo -e "   Please add this to the ${CYAN}mcpServers${NC} section in ${CYAN}$MCP_CONFIG_FILE${NC}:"
            echo ""
            echo -e "${BLUE}   \"datadog-traces\": {"
            echo -e "     \"command\": \"java\","
            echo -e "     \"args\": [\"-jar\", \"$JAR_PATH\"],"
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
        # No existing config - create new .claude.json with mcpServers
        write_new_config
    fi
    echo ""
}

write_new_config() {
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
    echo -e "${GREEN}✓ Created .claude.json configuration${NC}"
}

# Cleanup
cleanup() {
    if [ -n "$TEMP_BUILD_DIR" ] && [ -d "$TEMP_BUILD_DIR" ]; then
        echo -e "🧹 Cleanin' up temporary files..."
        rm -rf "$TEMP_BUILD_DIR"
    fi
}

# Show completion message
show_completion() {
    echo -e "${CYAN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo ""
    echo -e "${GREEN}🎉 YARR! Installation complete, Captain! 🏴‍☠️${NC}"
    echo ""
    echo -e "╭────────────────────────────────────────────────────────────────╮"
    echo -e "│                                                                │"
    echo -e "│  ${CYAN}JAR Location:${NC}    $MCP_APPS_DIR/$JAR_NAME"
    echo -e "│  ${CYAN}Config File:${NC}     $MCP_CONFIG_FILE"
    echo -e "│  ${CYAN}Version:${NC}         $STABLE_TAG"
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
}

# Main execution
main() {
    check_requirements
    setup_paths
    download_and_build
    install_jar
    get_credentials
    configure_claude
    cleanup
    show_completion
}

# Trap to cleanup on error
trap cleanup EXIT

# Run main
main

#!/usr/bin/env bash
#
# Installs or updates MabScope on an Ubuntu 24.04 server: the REST API runs as a systemd service
# on 127.0.0.1:7071, and Caddy serves the web client and forwards /api to it, over HTTPS with a
# Let's Encrypt certificate.
#
# The server can host other applications, such as ProteoSpectra-Explorer: each one keeps its site
# in /etc/caddy/sites/<domain>.caddy, imported by the main Caddyfile.
#
# Upload this script next to the built client (web/dist), then run it as root:
#   bash setup-server.sh mabscope.maximeethier.com
# Running it again pulls the latest code from GitHub and redeploys.

set -euo pipefail

DOMAIN="${1:?usage: setup-server.sh <domain>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO=https://github.com/Aldriel/MabScope.git
APP=/opt/mabscope
WEB=/var/www/mabscope
SITES=/etc/caddy/sites

if [[ ! -f "$HERE/dist/index.html" ]]; then
  echo "Missing $HERE/dist: upload web/dist next to this script." >&2
  exit 1
fi

# Maven and two JVMs need swap on a small server to avoid being killed for lack of memory.
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y openjdk-21-jdk-headless maven git caddy ufw unattended-upgrades

ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

id mabscope &>/dev/null || useradd --system --home-dir "$APP" --shell /usr/sbin/nologin mabscope

if [[ -d "$APP/src/.git" ]]; then
  git -C "$APP/src" pull --ff-only
else
  mkdir -p "$APP"
  git clone --depth 1 "$REPO" "$APP/src"
fi

cd "$APP/src"
bash deploy/install-pride-core.sh "$APP"

# The tests and the style checks already ran before the code was pushed.
mvn -B -q -pl server -am install -DskipTests -Dcheckstyle.skip -Djacoco.skip
rm -rf "$APP/lib"
mvn -B -q -pl server dependency:copy-dependencies \
  -DincludeScope=runtime -DoutputDirectory="$APP/lib"
cp server/target/mabscope-server-*.jar "$APP/lib/"

cat > /etc/systemd/system/mabscope.service <<EOF
[Unit]
Description=MabScope REST API
After=network-online.target
Wants=network-online.target

[Service]
User=mabscope
Environment=PORT=7071
# -Xmx leaves room for the system, Caddy and the other applications of the server.
ExecStart=/usr/bin/java -Xms128m -Xmx384m -Djava.awt.headless=true \\
    -cp "$APP/lib/*" com.bioinfo.mabscope.server.ApiServer
Restart=always
RestartSec=5
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF

mkdir -p "$WEB"
rm -rf "${WEB:?}"/*
cp -r "$HERE/dist/." "$WEB/"

# The main Caddyfile only imports the sites. A Caddyfile written by an older script, which held a
# single site, is kept as the file of that site; the default page of the package is dropped.
mkdir -p "$SITES"
if ! grep -qs "^import $SITES/\*.caddy" /etc/caddy/Caddyfile; then
  if grep -qs 'reverse_proxy' /etc/caddy/Caddyfile; then
    previous="$(awk 'NF && $1 !~ /^#/ { print $1; exit }' /etc/caddy/Caddyfile)"
    cp /etc/caddy/Caddyfile "$SITES/$previous.caddy"
  fi
  echo "import $SITES/*.caddy" > /etc/caddy/Caddyfile
fi

cat > "$SITES/$DOMAIN.caddy" <<EOF
$DOMAIN {
	encode zstd gzip

	handle /api/* {
		reverse_proxy 127.0.0.1:7071
	}

	handle {
		root * $WEB
		try_files {path} /index.html
		file_server
	}

	@assets path /assets/*
	header @assets Cache-Control "public, max-age=31536000, immutable"
}
EOF
caddy validate --adapter caddyfile --config /etc/caddy/Caddyfile

systemctl daemon-reload
systemctl enable mabscope caddy
systemctl restart mabscope
systemctl reload caddy || systemctl restart caddy

sleep 5
if curl -fsS http://127.0.0.1:7071/api/health; then
  echo
  echo "API is running. Open https://$DOMAIN once the DNS record points to this server."
else
  echo "The API did not answer; see: journalctl -u mabscope -n 50" >&2
  exit 1
fi

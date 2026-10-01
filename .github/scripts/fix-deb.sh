#!/usr/bin/env bash
# Post-process the jpackage-built .deb so it behaves on more machines than the raw output does:
#
#   * Depends     accept both the "t64" library names (Debian 13 / Ubuntu 24.04, where CI builds) and the
#                 older ones (Debian 12 / Ubuntu 22.04). jpackage only records the names of the build machine.
#   * postinst /  a failing `xdg-desktop-menu` (minimal or headless systems have no menu directory) must not leave
#     prerm       the package half-configured and impossible to remove. Fall back to a plain .desktop file.
#   * Maintainer  jpackage wraps the e-mail address twice.
#   * compression xz, so dpkg versions older than 1.21.18 can still read the package (zstd needs a newer dpkg).
#
# Usage: fix-deb.sh path/to/file.deb
# The file is replaced in place, only when the new package was built and passes its own checks.
set -euo pipefail

deb="$(readlink -f "${1:?usage: fix-deb.sh file.deb}")"
[ -s "$deb" ] || { echo "fix-deb: $deb not found or empty" >&2; exit 2; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
root="$work/root"
mkdir "$root"
dpkg-deb -R "$deb" "$root"
ctl="$root/DEBIAN"
for f in control postinst prerm; do
  [ -f "$ctl/$f" ] || { echo "fix-deb: DEBIAN/$f is missing - unexpected jpackage layout" >&2; exit 3; }
done

# ---- control ----------------------------------------------------------------------------------
sed -i -E \
  -e 's/^Maintainer:.*/Maintainer: ObsiFox Studio <dev@obsifox.invalid>/' \
  -e 's/^Section:.*/Section: games/' \
  "$ctl/control"
grep -q 'libasound2t64 |' "$ctl/control" \
  || sed -i -E '/^Depends:/ s/(^|[ ,])libasound2t64([ ,]|$)/\1libasound2t64 | libasound2\2/' "$ctl/control"
grep -q 'libpng16-16t64 |' "$ctl/control" \
  || sed -i -E '/^Depends:/ s/(^|[ ,])libpng16-16t64([ ,]|$)/\1libpng16-16t64 | libpng16-16\2/' "$ctl/control"

# ---- maintainer scripts -----------------------------------------------------------------------
grep -q '^xdg-desktop-menu install '   "$ctl/postinst" || { echo "fix-deb: no menu install line in postinst" >&2; exit 4; }
grep -q '^xdg-desktop-menu uninstall ' "$ctl/prerm"    || { echo "fix-deb: no menu uninstall line in prerm" >&2; exit 4; }

awk '
/^xdg-desktop-menu install / {
  print "# Menu entry. Minimal or headless systems have no xdg menu directory: use a plain .desktop file there"
  print "# instead of failing the whole installation."
  print "if ! xdg-desktop-menu install " $NF " >/dev/null 2>&1; then"
  print "    mkdir -p /usr/share/applications"
  print "    cp " $NF " /usr/share/applications/obsilauncher.desktop || true"
  print "fi"
  next
}
{ print }' "$ctl/postinst" > "$work/postinst.new"
cat "$work/postinst.new" > "$ctl/postinst"

awk '
/^xdg-desktop-menu uninstall / {
  print "xdg-desktop-menu uninstall " $NF " >/dev/null 2>&1 || true"
  print "rm -f /usr/share/applications/obsilauncher.desktop"
  next
}
{ print }' "$ctl/prerm" > "$work/prerm.new"
cat "$work/prerm.new" > "$ctl/prerm"
chmod 0755 "$ctl/postinst" "$ctl/prerm"

# ---- rebuild and verify -----------------------------------------------------------------------
out="$work/out.deb"
dpkg-deb --root-owner-group -Zxz -b "$root" "$out" >/dev/null

# the payload must be byte-for-byte the same file list with the same modes
list() { dpkg-deb -c "$1" | awk '{print $1, $2, $6}' | sort -k3; }
diff <(list "$deb") <(list "$out") >/dev/null || { echo "fix-deb: file list changed - keeping the original" >&2; exit 5; }
dpkg-deb -I "$out" control | grep -q '^Maintainer: ObsiFox Studio <dev@obsifox.invalid>$' \
  || { echo "fix-deb: control check failed" >&2; exit 6; }

mv "$out" "$deb"
echo "fix-deb: rewrote $(basename "$deb")"
dpkg-deb -I "$deb" control | grep -E '^(Package|Version|Maintainer|Section|Depends):' | cut -c1-200

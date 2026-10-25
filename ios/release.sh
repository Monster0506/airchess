#!/usr/bin/env bash
set -euo pipefail

mode=${1:-unsigned}
case "$mode" in
    unsigned|testflight) ;;
    *) echo "Usage: ios/release.sh unsigned|testflight" >&2; exit 2 ;;
esac

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
out="$root/build/ios-release"
mkdir -p "$out"
archive="$out/AirChess.xcarchive"
bundle_id=${AIRCHESS_BUNDLE_ID:-com.airchess}
version=${AIRCHESS_VERSION:-1.0.0}
build_number=${AIRCHESS_BUILD_NUMBER:-1}
settings=("AIRCHESS_BUNDLE_ID=$bundle_id" "MARKETING_VERSION=$version" "CURRENT_PROJECT_VERSION=$build_number")

if [[ -n ${AIRCHESS_SERVER_URL:-} ]]; then
    settings+=("AIRCHESS_SERVER_URL=$AIRCHESS_SERVER_URL")
fi

keychain=""
profile=""
key_file=""
secret_dir=""
cleanup() {
    if [[ -n "$keychain" ]]; then security delete-keychain "$keychain" || true; fi
    if [[ -n "$profile" ]]; then rm -f "$profile"; fi
    if [[ -n "$key_file" ]]; then rm -f "$key_file"; fi
    if [[ -n "$secret_dir" ]]; then rm -f "$secret_dir/certificate.p12" "$secret_dir/profile.mobileprovision" "$secret_dir/profile.plist"; rmdir "$secret_dir"; fi
}
trap cleanup EXIT

if [[ "$mode" == testflight ]]; then
    required=(APPLE_TEAM_ID IOS_CERTIFICATE_BASE64 IOS_CERTIFICATE_PASSWORD IOS_PROFILE_BASE64 APP_STORE_KEY_ID APP_STORE_ISSUER_ID APP_STORE_PRIVATE_KEY_BASE64)
    for name in "${required[@]}"; do
        if [[ -z ${!name:-} ]]; then echo "Missing release secret: $name" >&2; exit 1; fi
    done
    if [[ -z ${AIRCHESS_BUNDLE_ID:-} ]]; then echo "Set AIRCHESS_BUNDLE_ID to the registered app ID." >&2; exit 1; fi

    secret_dir=$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/airchess-signing.XXXXXX")
    umask 077
    printf '%s' "$IOS_CERTIFICATE_BASE64" | base64 --decode > "$secret_dir/certificate.p12"
    printf '%s' "$IOS_PROFILE_BASE64" | base64 --decode > "$secret_dir/profile.mobileprovision"
    security cms -D -i "$secret_dir/profile.mobileprovision" > "$secret_dir/profile.plist"
    plist=/usr/libexec/PlistBuddy
    profile_id=$($plist -c 'Print :UUID' "$secret_dir/profile.plist")
    profile_name=$($plist -c 'Print :Name' "$secret_dir/profile.plist")
    profile_team=$($plist -c 'Print :TeamIdentifier:0' "$secret_dir/profile.plist")
    profile_app=$($plist -c 'Print :Entitlements:application-identifier' "$secret_dir/profile.plist")
    [[ "$profile_team" == "$APPLE_TEAM_ID" && "$profile_app" == "$APPLE_TEAM_ID.$bundle_id" ]] || {
        echo "The provisioning profile must match APPLE_TEAM_ID and AIRCHESS_BUNDLE_ID." >&2; exit 1;
    }

    keychain="$secret_dir/signing.keychain-db"
    keychain_password=$(openssl rand -hex 32)
    security create-keychain -p "$keychain_password" "$keychain"
    security set-keychain-settings -lut 21600 "$keychain"
    security unlock-keychain -p "$keychain_password" "$keychain"
    security import "$secret_dir/certificate.p12" -P "$IOS_CERTIFICATE_PASSWORD" -A -t cert -f pkcs12 -k "$keychain"
    security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$keychain_password" "$keychain"
    security list-keychains -d user -s "$keychain" "$HOME/Library/Keychains/login.keychain-db"
    profile_dir="$HOME/Library/MobileDevice/Provisioning Profiles"
    mkdir -p "$profile_dir"
    profile="$profile_dir/$profile_id.mobileprovision"
    cp "$secret_dir/profile.mobileprovision" "$profile"
    settings+=("DEVELOPMENT_TEAM=$APPLE_TEAM_ID" "CODE_SIGN_STYLE=Manual" "CODE_SIGN_IDENTITY=Apple Distribution" "PROVISIONING_PROFILE_SPECIFIER=$profile_name")
else
    settings+=(CODE_SIGNING_ALLOWED=NO)
fi

if [[ ! -d ios/stockfish/build/Stockfish.xcframework ]]; then
    bash ios/stockfish/build.sh ios
fi
(cd iosApp && xcodegen generate)
xcodebuild archive -project iosApp/AirChess.xcodeproj -scheme AirChess \
    -configuration Release -destination 'generic/platform=iOS' \
    -archivePath "$archive" -derivedDataPath "$out/DerivedData" "${settings[@]}"

if [[ "$mode" == unsigned ]]; then
    ditto -c -k --sequesterRsrc --keepParent "$archive" "$out/AirChess-unsigned.xcarchive.zip"
    echo "Unsigned archive: $out/AirChess-unsigned.xcarchive.zip (not installable on an iPhone)"
    exit 0
fi

export_options="$out/ExportOptions.plist"
/usr/libexec/PlistBuddy -c Clear "$export_options"
/usr/libexec/PlistBuddy -c 'Add :method string app-store-connect' "$export_options"
/usr/libexec/PlistBuddy -c 'Add :destination string export' "$export_options"
/usr/libexec/PlistBuddy -c 'Add :signingStyle string manual' "$export_options"
/usr/libexec/PlistBuddy -c "Add :teamID string $APPLE_TEAM_ID" "$export_options"
/usr/libexec/PlistBuddy -c 'Add :manageAppVersionAndBuildNumber bool false' "$export_options"
/usr/libexec/PlistBuddy -c 'Add :provisioningProfiles dict' "$export_options"
/usr/libexec/PlistBuddy -c "Add :provisioningProfiles:$bundle_id string $profile_name" "$export_options"
xcodebuild -exportArchive -archivePath "$archive" -exportPath "$out/export" -exportOptionsPlist "$export_options"

key_dir="$HOME/.appstoreconnect/private_keys"
mkdir -p "$key_dir"
key_file="$key_dir/AuthKey_$APP_STORE_KEY_ID.p8"
printf '%s' "$APP_STORE_PRIVATE_KEY_BASE64" | base64 --decode > "$key_file"
xcrun altool --validate-app --type ios --file "$out/export/AirChess.ipa" --apiKey "$APP_STORE_KEY_ID" --apiIssuer "$APP_STORE_ISSUER_ID"
xcrun altool --upload-app --type ios --file "$out/export/AirChess.ipa" --apiKey "$APP_STORE_KEY_ID" --apiIssuer "$APP_STORE_ISSUER_ID"
echo "Upload accepted. Processing and TestFlight tester assignment happen in App Store Connect."

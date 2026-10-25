# Synthesizes vo-lines.json to assets/vo/vo-NN.wav (44.1 kHz, 16-bit, mono) with a
# Windows OneCore voice via Windows.Media.SpeechSynthesis, and writes durations.json.
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File make-vo.ps1 [-VoiceName "Microsoft Mark"] [-Rate -0.2]
# Rate uses the -1..0 "calm" scale: WinRT SpeakingRate = 1 + 0.5 * Rate.
param(
  [string]$VoiceName = 'Microsoft Mark',
  [double]$Rate = -0.2
)
$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Runtime.WindowsRuntime
[void][Windows.Media.SpeechSynthesis.SpeechSynthesizer, Windows.Media.SpeechSynthesis, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]

$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() |
  Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' } |
  Select-Object -First 1

function Await-WinRt([object]$op, [Type]$resultType) {
  $task = $asTaskGeneric.MakeGenericMethod($resultType).Invoke($null, @($op))
  $task.Wait(-1) | Out-Null
  return $task.Result
}

$voiceDir = (New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot '..\..\assets\vo')).FullName
$lines = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'vo-lines.json') | ConvertFrom-Json

$voice = [Windows.Media.SpeechSynthesis.SpeechSynthesizer]::AllVoices | Where-Object { $_.DisplayName -eq $VoiceName } | Select-Object -First 1
if (-not $voice) { throw "Voice '$VoiceName' not found" }

$synth = New-Object Windows.Media.SpeechSynthesis.SpeechSynthesizer
$synth.Voice = $voice
$synth.Options.SpeakingRate = 1.0 + 0.5 * $Rate

$durations = @()
foreach ($line in $lines) {
  $stream = Await-WinRt ($synth.SynthesizeTextToStreamAsync($line.text)) ([Windows.Media.SpeechSynthesis.SpeechSynthesisStream])
  $size = [uint32]$stream.Size
  $reader = New-Object Windows.Storage.Streams.DataReader($stream)
  [void](Await-WinRt ($reader.LoadAsync($size)) ([uint32]))
  $bytes = New-Object byte[] $size
  $reader.ReadBytes($bytes)
  $reader.Dispose()
  $stream.Dispose()

  $raw = [IO.Path]::Combine([IO.Path]::GetTempPath(), "airchess-vo-$($line.id).wav")
  [IO.File]::WriteAllBytes($raw, $bytes)
  $out = Join-Path $voiceDir "vo-$($line.id).wav"
  & ffmpeg -v error -y -i $raw -ar 44100 -ac 1 -c:a pcm_s16le $out
  if ($LASTEXITCODE -ne 0) { throw "ffmpeg failed for line $($line.id)" }
  Remove-Item $raw

  $dur = [double]((& ffprobe -v error -show_entries format=duration -of csv=p=0 $out) | Select-Object -First 1)
  $durations += [pscustomobject]@{ id = $line.id; file = "vo-$($line.id).wav"; seconds = [math]::Round($dur, 3) }
  Write-Host ("vo-{0}.wav  {1:N3} s" -f $line.id, $dur)
}

$durations | ConvertTo-Json | Set-Content -Encoding UTF8 (Join-Path $voiceDir 'durations.json')
Write-Host "Voice: $($voice.DisplayName) ($($voice.Language)), rate $Rate (SpeakingRate $($synth.Options.SpeakingRate))"

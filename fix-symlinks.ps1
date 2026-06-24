# Fix broken symlinks in assets/shared/
$projectRoot = 'd:\project\andriod\trime'
$assetsDir = Join-Path $projectRoot 'app\src\main\assets\shared'

# List of broken symlinks and their targets (relative to project root)
$brokenLinks = @(
    @{file='default.yaml'; target='app\data\rime\prelude\default.yaml'},
    @{file='essay.txt'; target='app\data\rime\essay\essay.txt'},
    @{file='key_bindings.yaml'; target='app\data\rime\prelude\key_bindings.yaml'},
    @{file='luna_pinyin.dict.yaml'; target='app\data\rime\luna-pinyin\luna_pinyin.dict.yaml'},
    @{file='luna_pinyin.schema.yaml'; target='app\data\rime\luna-pinyin\luna_pinyin.schema.yaml'},
    @{file='luna_pinyin_fluency.schema.yaml'; target='app\data\rime\luna-pinyin\luna_pinyin_fluency.schema.yaml'},
    @{file='luna_pinyin_simp.schema.yaml'; target='app\data\rime\luna-pinyin\luna_pinyin_simp.schema.yaml'},
    @{file='luna_pinyin_tw.schema.yaml'; target='app\data\rime\luna-pinyin\luna_pinyin_tw.schema.yaml'},
    @{file='luna_quanpin.schema.yaml'; target='app\data\rime\luna-pinyin\luna_quanpin.schema.yaml'},
    @{file='pinyin.yaml'; target='app\data\rime\luna-pinyin\pinyin.yaml'},
    @{file='punctuation.yaml'; target='app\data\rime\prelude\punctuation.yaml'},
    @{file='stroke.dict.yaml'; target='app\data\rime\stroke\stroke.dict.yaml'},
    @{file='stroke.schema.yaml'; target='app\data\rime\stroke\stroke.schema.yaml'},
    @{file='symbols.yaml'; target='app\data\rime\prelude\symbols.yaml'}
)

foreach ($link in $brokenLinks) {
    $filePath = Join-Path $assetsDir $link.file
    $targetPath = Join-Path $projectRoot $link.target
    
    if (-not (Test-Path $targetPath)) {
        Write-Host "SKIP: Target not found: $targetPath"
        continue
    }
    
    if (Test-Path $filePath) {
        Remove-Item $filePath -Force
    }
    
    # Create symlink
    New-Item -ItemType SymbolicLink -Path $filePath -Target $targetPath -Force
    Write-Host "FIXED: $link.file -> $($link.target)"
}

Write-Host "`nDone!"

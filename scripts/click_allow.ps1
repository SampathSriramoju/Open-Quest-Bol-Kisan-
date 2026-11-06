Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes

$root = [System.Windows.Automation.AutomationElement]::RootElement

# Find all windows
$windows = $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)
Write-Output "Found $($windows.Count) desktop windows."

foreach ($win in $windows) {
    $name = $win.Current.Name
    if ($name -like "*Android*" -or $name -like "*Emulator*" -or $name -like "*Studio*" -or $name -like "*qemu*") {
        Write-Output "Checking Window: $name"
        
        # Look for buttons in this window
        $btnCondition = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty, [System.Windows.Automation.ControlType]::Button)
        $buttons = $win.FindAll([System.Windows.Automation.TreeScope]::Descendants, $btnCondition)
        foreach ($btn in $buttons) {
            $btnName = $btn.Current.Name
            if ($btnName -like "*Allow*" -or $btnName -like "*OK*" -or $btnName -like "*Authorize*") {
                Write-Output "  Found button: $btnName in $name"
                try {
                    $inv = $btn.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)
                    if ($inv) {
                        $inv.Invoke()
                        Write-Output "  -> Successfully invoked: $btnName"
                    }
                } catch {
                    Write-Output "  -> Could not invoke: $_"
                }
            }
        }
    }
}

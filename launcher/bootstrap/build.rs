fn main() {
    #[cfg(windows)]
    winresource::WindowsResource::new()
        .set_icon("../src/main/resources/net/modtale/launcher/ui/nativefx/assets/favicon.ico")
        .set("ProductName", "Modtale Launcher")
        .set("FileDescription", "Modtale Launcher")
        .compile()
        .expect("compile launcher resources");
}

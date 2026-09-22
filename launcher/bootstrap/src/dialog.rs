// Use the GTK already required by JavaFX on Linux, including inside Flatpak.
// This avoids depending on a separately installed zenity/kdialog executable.
pub fn message(title: &str, text: &str, error: bool) {
    #[cfg(target_os = "linux")]
    if gtk_message(title, text, error).is_some() {
        return;
    }
    tinyfiledialogs::message_box_ok(
        title,
        text,
        if error {
            tinyfiledialogs::MessageBoxIcon::Error
        } else {
            tinyfiledialogs::MessageBoxIcon::Info
        },
    );
}

#[cfg(target_os = "linux")]
fn gtk_message(title: &str, text: &str, error: bool) -> Option<()> {
    use std::{
        ffi::{CString, c_char, c_int, c_void},
        ptr,
    };
    // All pointers belong to GTK; the modal loop finishes before the widget is destroyed.
    unsafe {
        let library = libloading::Library::new("libgtk-3.so.0").ok()?;
        let init = library
            .get::<unsafe extern "C" fn(*mut c_int, *mut *mut *mut c_char) -> c_int>(
                b"gtk_init_check\0",
            )
            .ok()?;
        if init(ptr::null_mut(), ptr::null_mut()) == 0 {
            return None;
        }
        let create = library
            .get::<unsafe extern "C" fn(
                *mut c_void,
                c_int,
                c_int,
                c_int,
                *const c_char,
                ...
            ) -> *mut c_void>(b"gtk_message_dialog_new\0")
            .ok()?;
        let set_title = library
            .get::<unsafe extern "C" fn(*mut c_void, *const c_char)>(b"gtk_window_set_title\0")
            .ok()?;
        let run = library
            .get::<unsafe extern "C" fn(*mut c_void) -> c_int>(b"gtk_dialog_run\0")
            .ok()?;
        let destroy = library
            .get::<unsafe extern "C" fn(*mut c_void)>(b"gtk_widget_destroy\0")
            .ok()?;
        let title = CString::new(title.replace('\0', " ")).ok()?;
        let text = CString::new(text.replace('\0', " ")).ok()?;
        let widget = create(
            ptr::null_mut(),
            1,
            if error { 3 } else { 0 },
            1,
            c"%s".as_ptr(),
            text.as_ptr(),
        );
        if widget.is_null() {
            return None;
        }
        set_title(widget, title.as_ptr());
        run(widget);
        destroy(widget);
        // GTK registers process-wide callbacks; do not unload it before process exit.
        std::mem::forget(library);
    }
    Some(())
}

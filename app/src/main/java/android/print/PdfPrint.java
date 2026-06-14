package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;

/**
 * Treibt einen {@link PrintDocumentAdapter} (z. B. von
 * {@code WebView.createPrintDocumentAdapter()}) von Hand an und schreibt das Ergebnis
 * direkt in eine PDF-Datei — OHNE System-Druckdialog.
 *
 * Liegt bewusst im Package {@code android.print}: die Konstruktoren von
 * {@link PrintDocumentAdapter.LayoutResultCallback} /
 * {@link PrintDocumentAdapter.WriteResultCallback} sind paket-privat und nur aus diesem
 * Package subclassbar (Standard-Trick für „WebView → PDF" ohne Druckdialog).
 */
public final class PdfPrint {

    /** Rückmeldung, ob die PDF erfolgreich geschrieben wurde. */
    public interface Callback {
        void onFinished(boolean success);
    }

    private PdfPrint() {}

    public static void print(final PrintDocumentAdapter adapter, final File outFile, final Callback cb) {
        final PrintAttributes attrs = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build();
        final File parent = outFile.getParentFile();
        if (parent != null) parent.mkdirs();

        adapter.onLayout(null, attrs, new CancellationSignal(),
                new PrintDocumentAdapter.LayoutResultCallback() {
                    @Override
                    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                        final ParcelFileDescriptor pfd = openFd(outFile);
                        if (pfd == null) {
                            cb.onFinished(false);
                            return;
                        }
                        adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, new CancellationSignal(),
                                new PrintDocumentAdapter.WriteResultCallback() {
                                    @Override
                                    public void onWriteFinished(PageRange[] pages) {
                                        close(pfd);
                                        boolean ok = pages != null && pages.length > 0;
                                        if (!ok) deleteQuietly(outFile);
                                        cb.onFinished(ok);
                                    }

                                    @Override
                                    public void onWriteFailed(CharSequence error) {
                                        close(pfd);
                                        deleteQuietly(outFile);
                                        cb.onFinished(false);
                                    }
                                });
                    }

                    @Override
                    public void onLayoutFailed(CharSequence error) {
                        cb.onFinished(false);
                    }
                }, null);
    }

    private static ParcelFileDescriptor openFd(File f) {
        try {
            return ParcelFileDescriptor.open(f,
                    ParcelFileDescriptor.MODE_READ_WRITE
                            | ParcelFileDescriptor.MODE_CREATE
                            | ParcelFileDescriptor.MODE_TRUNCATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static void close(ParcelFileDescriptor pfd) {
        try {
            pfd.close();
        } catch (Exception ignored) {
        }
    }

    private static void deleteQuietly(File f) {
        try {
            if (f.exists()) f.delete();
        } catch (Exception ignored) {
        }
    }
}

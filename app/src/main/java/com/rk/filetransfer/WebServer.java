package com.rk.filetransfer;


import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.Html;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class WebServer extends NanoHTTPD {


    private String text;

    enum MODE {SINGLE, MULTIPLE, TEXT}

    private MODE mode;
    private Uri[] uri;
    private final Context context;

    public void reset(MODE mode, String text, Uri[] uri, Uri uri1) {
        this.mode = mode;
        if (mode == MODE.TEXT)
            this.text = text;
        else if (mode == MODE.MULTIPLE)
            this.uri = uri;
        else this.uri = new Uri[]{uri1};
    }

    WebServer(Context context, Uri uri) {
        super(8080);
        mode = MODE.SINGLE;
        this.uri = new Uri[]{uri};
        this.context = context;
    }


    public WebServer(Context context, String text) {
        super(8080);
        mode = MODE.TEXT;
        this.text = text;
        this.context = context;
    }

    WebServer(Context context, Uri[] uri) {
        super(8080);
        mode = MODE.MULTIPLE;
        this.uri = uri;
        this.context = context;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (mode == MODE.SINGLE)
            return serveSingle();
        else if (mode == MODE.MULTIPLE)
            return serveMultiple(session);
        else
            return serveText();
    }

    private Response serveText() {
        Log.d("abcd",text);
      return newFixedLengthResponse(context.getString(R.string.html_text)+Html.escapeHtml(text)+context.getString(R.string.html_text_end));
    }

    private Response serveSingle() {
        Response response = null;
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri[0]);
            String mime = context.getContentResolver().getType(uri[0]);
            response = newFixedLengthResponse(Response.Status.OK, mime, inputStream, inputStream.available());
            response.addHeader("Content-Disposition", "attachment; filename=\"" + getFileName(uri[0]) + "\"");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response;
    }

    private Response serveMultiple(IHTTPSession session) {
        Response response = null;
        Uri uri = buildUri(session.getUri());
        if (uri == null) {
            String tmp = session.getUri();
            InputStream inputStream;
            switch (tmp) {
                case "/download.png":
                    inputStream = getInputStream(R.drawable.download);
                    break;
                case "/doc.png":
                    inputStream = getInputStream(R.drawable.doc);
                    break;
                case "/pdf.png":
                    inputStream = getInputStream(R.drawable.pdf);
                    break;
                case "/xls.png":
                    inputStream = getInputStream(R.drawable.xls);
                    break;
                case "/ppt.png":
                    inputStream = getInputStream(R.drawable.ppt);
                    break;
                case "/picture.png":
                    inputStream = getInputStream(R.drawable.picture);
                    break;
                case "/video.png":
                    inputStream = getInputStream(R.drawable.video);
                    break;
                case "/zip.png":
                    inputStream = getInputStream(R.drawable.zip);
                    break;
                case "/music.png":
                    inputStream = getInputStream(R.drawable.music);
                    break;
                case "/file.png":
                    inputStream = getInputStream(R.drawable.file);
                    break;
                case "/apk.png":
                    inputStream = getInputStream(R.drawable.apk);
                    break;
                default:
                    return newFixedLengthResponse(getIndexHtml());
            }
            try {
                response = newFixedLengthResponse(Response.Status.OK, "image/png", inputStream, inputStream.available());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        } else {
            try {
                InputStream inputStream = context.getContentResolver().openInputStream(uri);
                String mime = context.getContentResolver().getType(uri);
                response = newFixedLengthResponse(Response.Status.OK, mime, inputStream, inputStream.available());
                response.addHeader("Content-Disposition", "attachment; filename=\"" + getFileName(uri) + "\"");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return response;
        }
    }

    private InputStream getInputStream(int resId) {
        Bitmap bitmap = ((BitmapDrawable) (context.getResources().getDrawable(resId))).getBitmap();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] imageInByte = stream.toByteArray();
        return new ByteArrayInputStream(imageInByte);
    }

    private Uri buildUri(String uri) {
        if (uri.length() > 1) {
            try {
                int no = Integer.parseInt(uri.substring(1));
                if (no < this.uri.length)
                    return this.uri[no];
                else return null;
            } catch (NumberFormatException e) {
                return null;
            }
        } else return null;
    }

    private String getIndexHtml() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < uri.length; i++) {
            String name = getFileName(uri[i]);
            String size = getFileSize(uri[i]);
            String mime = context.getContentResolver().getType(uri[i]);
            String individual_html = context.getString(R.string.individual_item, getIconType(mime), name, size, "/" + i);
            sb.append(individual_html);
        }
        return context.getString(R.string.html_base) + " " + Build.MODEL + sb.toString() + context.getString(R.string.html_end);

    }

    private String getIconType(String mime) {
        Log.d("abcd", "mime = " + mime);
        if (mime.startsWith("image/"))
            return "/picture.png";
        else if (mime.startsWith("audio/"))
            return "/music.png";
        else if (mime.startsWith("video/"))
            return "/video.png";
        else if (mime.equals("application/pdf"))
            return "/pdf.png";
        else if (mime.equals("application/msword") || mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            return "/doc.png";
        else if (mime.equals("application/vnd.ms-excel") || mime.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            return "/xls.png";
        else if (mime.equals("application/vnd.ms-powerpoint") || mime.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
            return "/ppt.png";
        else if (mime.equals("application/x-rar-compressed") || mime.equals("application/zip"))
            return "/zip.png";
        else if (mime.equals("application/vnd.android.package-archive"))
            return "/apk.png";
        else return "/file.png";
    }

    @SuppressLint("DefaultLocale")
    private String getFileSize(Uri uri) {
        Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
        returnCursor.moveToFirst();
        float size = returnCursor.getLong(returnCursor.getColumnIndex(OpenableColumns.SIZE));
        returnCursor.close();
        long ONE_GB = 1024 * 1024 * 1024;
        long ONE_MB = 1024 * 1024;
        if (size < ONE_MB) {
            long ONE_KB = 1024;
            return String.format("%.1fKB", size / ONE_KB);
        } else if (size < ONE_GB) {
            return String.format("%.1fMB", size / ONE_MB);
        } else return String.format("%.1fGB", size / ONE_GB);
    }

    private String getFileName(Uri uri) {
        Cursor returnCursor = context.getContentResolver().query(uri, null, null, null, null);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
        returnCursor.close();
        return name;
    }
}

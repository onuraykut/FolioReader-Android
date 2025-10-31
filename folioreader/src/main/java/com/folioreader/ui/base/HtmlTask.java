package com.folioreader.ui.base;

import android.os.AsyncTask;
import android.util.Log;
import com.folioreader.util.AppUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Background async task which downloads the html content of a web page
 * from server
 *
 * @author by gautam on 12/6/17.
 */

public class HtmlTask extends AsyncTask<String, Void, String> {

    private static final String TAG = "HtmlTask";

    private HtmlTaskCallback callback;

    public HtmlTask(HtmlTaskCallback callback) {
        this.callback = callback;
    }

    @Override
    protected String doInBackground(String... urls) {
        if (urls.length == 1) {
            // Single URL - original behavior
            return downloadHtml(urls[0]);
        } else {
            // Multiple URLs - merge all chapters
            StringBuilder mergedHtml = new StringBuilder();
            for (int i = 0; i < urls.length; i++) {
                String html = downloadHtml(urls[i]);
                if (html != null) {
                    // Extract body content from HTML
                    String bodyContent = extractBodyContent(html);
                    if (i == 0) {
                        // First chapter - keep full HTML structure
                        // But replace closing body tag to allow appending
                        int bodyEndIndex = html.lastIndexOf("</body>");
                        if (bodyEndIndex != -1) {
                            mergedHtml.append(html.substring(0, bodyEndIndex));
                            mergedHtml.append("\n<hr class='chapter-separator' />\n");
                        } else {
                            mergedHtml.append(html);
                        }
                    } else if (i == urls.length - 1) {
                        // Last chapter - append content and close tags
                        mergedHtml.append(bodyContent);
                        mergedHtml.append("\n</body>\n</html>");
                    } else {
                        // Middle chapters - just append body content
                        mergedHtml.append(bodyContent);
                        mergedHtml.append("\n<hr class='chapter-separator' />\n");
                    }
                }
            }
            return mergedHtml.toString();
        }
    }

    private String downloadHtml(String strUrl) {
        try {
            URL url = new URL(strUrl);
            URLConnection urlConnection = url.openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, AppUtil.charsetNameForURLConnection(urlConnection)));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
            if (stringBuilder.length() > 0)
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "HtmlTask failed for URL: " + strUrl, e);
        }
        return null;
    }

    private String extractBodyContent(String html) {
        if (html == null) return "";

        // Find body content
        int bodyStart = html.indexOf("<body");
        if (bodyStart == -1) return html;

        bodyStart = html.indexOf(">", bodyStart) + 1;
        int bodyEnd = html.lastIndexOf("</body>");

        if (bodyEnd == -1) bodyEnd = html.length();

        return html.substring(bodyStart, bodyEnd);
    }

    @Override
    protected void onPostExecute(String htmlString) {
        if (htmlString != null) {
            callback.onReceiveHtml(htmlString);
        } else {
            callback.onError();
        }
        cancel(true);
    }
}

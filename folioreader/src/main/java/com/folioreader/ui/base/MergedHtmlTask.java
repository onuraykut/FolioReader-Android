package com.folioreader.ui.base;

import android.os.AsyncTask;
import android.util.Log;
import com.folioreader.util.AppUtil;
import org.readium.r2.shared.Link;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * Background async task which downloads and merges all chapter HTML contents
 * from server into a single HTML document
 *
 * @author FolioReader
 */
public class MergedHtmlTask extends AsyncTask<Void, Integer, String> {

    private static final String TAG = "MergedHtmlTask";

    private HtmlTaskCallback callback;
    private List<Link> spineReferences;
    private String streamerUrl;

    public MergedHtmlTask(HtmlTaskCallback callback, List<Link> spineReferences, String streamerUrl) {
        this.callback = callback;
        this.spineReferences = spineReferences;
        this.streamerUrl = streamerUrl;
    }

    @Override
    protected String doInBackground(Void... params) {
        StringBuilder mergedHtml = new StringBuilder();
        String firstChapterHtml = null;

        try {
            for (int i = 0; i < spineReferences.size(); i++) {
                Link link = spineReferences.get(i);
                String href = link.getHref();
                if (href == null || href.isEmpty()) continue;

                // Construct the full URL
                String chapterUrl = streamerUrl + href.substring(1);

                publishProgress(i + 1, spineReferences.size());

                String chapterHtml = fetchHtmlContent(chapterUrl);
                if (chapterHtml == null) continue;

                if (i == 0) {
                    // For the first chapter, keep the full HTML structure
                    firstChapterHtml = chapterHtml;

                    // Extract body content from first chapter
                    String bodyContent = extractBodyContent(chapterHtml);
                    if (bodyContent != null) {
                        mergedHtml.append("<div class=\"chapter\" id=\"chapter-").append(i).append("\" data-chapter=\"").append(i).append("\">");
                        mergedHtml.append(bodyContent);
                        mergedHtml.append("</div>\n");
                    }
                } else {
                    // For subsequent chapters, extract only body content
                    String bodyContent = extractBodyContent(chapterHtml);
                    if (bodyContent != null) {
                        mergedHtml.append("<div class=\"chapter\" id=\"chapter-").append(i).append("\" data-chapter=\"").append(i).append("\">");
                        mergedHtml.append(bodyContent);
                        mergedHtml.append("</div>\n");
                    }
                }
            }

            // Now create the final merged HTML with the head from first chapter
            if (firstChapterHtml != null && mergedHtml.length() > 0) {
                return constructMergedHtml(firstChapterHtml, mergedHtml.toString());
            }

        } catch (Exception e) {
            Log.e(TAG, "MergedHtmlTask failed", e);
        }

        return null;
    }

    private String fetchHtmlContent(String urlString) {
        try {
            URL url = new URL(urlString);
            URLConnection urlConnection = url.openConnection();
            InputStream inputStream = urlConnection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(inputStream, AppUtil.charsetNameForURLConnection(urlConnection))
            );
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
            bufferedReader.close();
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to fetch HTML from: " + urlString, e);
            return null;
        }
    }

    private String extractBodyContent(String html) {
        if (html == null) return null;

        // Find body opening and closing tags
        int bodyStart = html.indexOf("<body");
        if (bodyStart == -1) bodyStart = html.indexOf("<BODY");
        if (bodyStart == -1) return null;

        bodyStart = html.indexOf(">", bodyStart) + 1;

        int bodyEnd = html.lastIndexOf("</body>");
        if (bodyEnd == -1) bodyEnd = html.lastIndexOf("</BODY>");
        if (bodyEnd == -1) return null;

        return html.substring(bodyStart, bodyEnd).trim();
    }

    private String constructMergedHtml(String firstChapterHtml, String mergedBodyContent) {
        // Extract head section from first chapter
        int headStart = firstChapterHtml.indexOf("<head");
        if (headStart == -1) headStart = firstChapterHtml.indexOf("<HEAD");

        int headEnd = firstChapterHtml.indexOf("</head>");
        if (headEnd == -1) headEnd = firstChapterHtml.indexOf("</HEAD>");

        String headSection = "";
        if (headStart != -1 && headEnd != -1) {
            headSection = firstChapterHtml.substring(headStart, headEnd + 7); // 7 = length of "</head>"
        }

        // Extract html opening tag
        int htmlStart = firstChapterHtml.indexOf("<html");
        if (htmlStart == -1) htmlStart = firstChapterHtml.indexOf("<HTML");
        int htmlTagEnd = -1;
        if (htmlStart != -1) {
            htmlTagEnd = firstChapterHtml.indexOf(">", htmlStart) + 1;
        }

        String htmlOpenTag = "";
        if (htmlStart != -1 && htmlTagEnd > htmlStart) {
            htmlOpenTag = firstChapterHtml.substring(htmlStart, htmlTagEnd);
        } else {
            htmlOpenTag = "<html>";
        }

        // Construct the final merged HTML
        StringBuilder finalHtml = new StringBuilder();
        finalHtml.append(htmlOpenTag).append("\n");
        finalHtml.append(headSection).append("\n");
        finalHtml.append("<body class=\"merged-chapters\">\n");
        finalHtml.append("<div id=\"merged-content\">\n");
        finalHtml.append(mergedBodyContent);
        finalHtml.append("</div>\n");
        finalHtml.append("</body>\n");
        finalHtml.append("</html>");

        return finalHtml.toString();
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        Log.d(TAG, "Loading chapter " + values[0] + " of " + values[1]);
    }

    @Override
    protected void onPostExecute(String mergedHtmlString) {
        if (mergedHtmlString != null && !mergedHtmlString.isEmpty()) {
            Log.d(TAG, "Successfully merged " + spineReferences.size() + " chapters");
            callback.onReceiveHtml(mergedHtmlString);
        } else {
            Log.e(TAG, "Failed to merge chapters");
            callback.onError();
        }
        cancel(true);
    }
}


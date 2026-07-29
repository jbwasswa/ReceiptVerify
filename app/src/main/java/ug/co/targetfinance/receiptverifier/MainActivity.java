package ug.co.targetfinance.receiptverifier;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;

public class MainActivity extends Activity {
    private static final String VERIFY_BASE_URL = "https://targetfinance.co.ug/verify.php";

    private EditText ptidInput;
    private EditText codeInput;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        webView.setWebViewClient(new WebViewClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
    }

    private void buildUi() {
        int navy = Color.rgb(17, 42, 49);
        int teal = Color.rgb(0, 198, 155);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245, 248, 250));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(14));
        header.setBackgroundColor(navy);

        TextView title = new TextView(this);
        title.setText("Target Receipt Verification");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setGravity(Gravity.START);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Enter PTID and verification code");
        subtitle.setTextColor(Color.rgb(190, 225, 220));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(4), 0, 0);
        header.addView(subtitle);

        root.addView(header);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(16), dp(18), dp(12));
        form.setBackgroundColor(Color.WHITE);

        ptidInput = makeInput("PTID", InputType.TYPE_CLASS_NUMBER);
        codeInput = makeInput("Verification Code", InputType.TYPE_CLASS_NUMBER);
        Button verifyButton = new Button(this);
        verifyButton.setText("Verify Receipt");
        verifyButton.setTextColor(Color.WHITE);
        verifyButton.setTextSize(16);
        verifyButton.setAllCaps(false);
        verifyButton.setBackgroundColor(teal);
        verifyButton.setOnClickListener(v -> verifyReceipt());

        form.addView(ptidInput);
        form.addView(codeInput);
        form.addView(verifyButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        root.addView(form);

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    private EditText makeInput(String hint, int inputType) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(inputType);
        input.setTextSize(16);
        input.setPadding(dp(12), 0, dp(12), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        params.setMargins(0, 0, 0, dp(10));
        input.setLayoutParams(params);
        return input;
    }

    private void verifyReceipt() {
        String ptid = ptidInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();

        if (ptid.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Enter both PTID and verification code", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String url = VERIFY_BASE_URL
                    + "?ptid=" + URLEncoder.encode(ptid, "UTF-8")
                    + "&vc=" + URLEncoder.encode(code, "UTF-8");
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        } catch (Exception ex) {
            Toast.makeText(this, "Could not build verification URL", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}

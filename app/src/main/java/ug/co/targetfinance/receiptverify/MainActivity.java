package ug.co.targetfinance.receiptverify;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String VERIFY_BASE_URL = "https://targetfinance.co.ug/verify.php";

    private final int navy = Color.rgb(13, 43, 50);
    private final int deepNavy = Color.rgb(7, 25, 29);
    private final int teal = Color.rgb(0, 198, 155);
    private final int muted = Color.rgb(92, 111, 122);
    private final int danger = Color.rgb(200, 35, 51);
    private final int border = Color.rgb(207, 222, 224);
    private boolean applyingParsedInput = false;

    private EditText ptidInput;
    private EditText codeInput;
    private Button verifyButton;
    private LinearLayout inputScreen;
    private LinearLayout resultScreen;
    private TextView statusText;
    private ProgressBar progressBar;
    private ProgressBar resultProgressBar;
    private TextView resultStatusText;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        updateValidationState();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                resultProgressBar.setVisibility(View.VISIBLE);
                resultStatusText.setVisibility(View.VISIBLE);
                resultStatusText.setText("Verifying receipt...");
                resultStatusText.setTextColor(muted);
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                resultProgressBar.setVisibility(View.GONE);
                resultStatusText.setText("Verification result loaded.");
                resultStatusText.setTextColor(teal);
                webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                resultProgressBar.setVisibility(View.GONE);
                resultStatusText.setVisibility(View.VISIBLE);
                resultStatusText.setText("Could not load the verification result. Check internet and try again.");
                resultStatusText.setTextColor(danger);
                webView.setVisibility(View.GONE);
            }
        });

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(241, 247, 247));
        root.setLayoutParams(matchParent());

        inputScreen = new LinearLayout(this);
        inputScreen.setOrientation(LinearLayout.VERTICAL);
        inputScreen.setLayoutParams(matchParent());
        inputScreen.addView(buildHeader());
        inputScreen.addView(buildForm());

        resultScreen = new LinearLayout(this);
        resultScreen.setOrientation(LinearLayout.VERTICAL);
        resultScreen.setLayoutParams(matchParent());
        resultScreen.setVisibility(View.GONE);
        resultScreen.addView(buildResultHeader());
        resultScreen.addView(buildResultLoadingBar());
        webView = new WebView(this);
        resultScreen.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        root.addView(inputScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        root.addView(resultScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(22), dp(22), dp(18));
        header.setBackgroundColor(navy);

        TextView brand = new TextView(this);
        brand.setText("TARGET FINANCE");
        brand.setTextColor(teal);
        brand.setTextSize(13);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setLetterSpacing(0.08f);
        header.addView(brand);

        TextView title = new TextView(this);
        title.setText("Receipt Verification");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(4), 0, 0);
        header.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Confirm payments using the PTID and verification code on the receipt.");
        subtitle.setTextColor(Color.rgb(184, 219, 215));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(6), 0, 0);
        header.addView(subtitle);

        return header;
    }

    private View buildResultLoadingBar() {
        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setPadding(dp(14), dp(10), dp(14), dp(10));
        loading.setBackgroundColor(Color.WHITE);

        resultStatusText = new TextView(this);
        resultStatusText.setText("Verifying receipt...");
        resultStatusText.setTextColor(muted);
        resultStatusText.setTextSize(13);
        resultStatusText.setGravity(Gravity.CENTER);
        loading.addView(resultStatusText);

        resultProgressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        resultProgressBar.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.setMargins(0, dp(8), 0, 0);
        loading.addView(resultProgressBar, progressParams);

        return loading;
    }

    private View buildResultHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(12), dp(12));
        header.setBackgroundColor(navy);

        Button backButton = new Button(this);
        backButton.setText("New Verification");
        backButton.setAllCaps(false);
        backButton.setTextColor(Color.WHITE);
        backButton.setTextSize(14);
        backButton.setTypeface(Typeface.DEFAULT_BOLD);
        backButton.setBackground(makeRoundRect(Color.rgb(20, 70, 78), Color.rgb(20, 70, 78), dp(10)));
        backButton.setOnClickListener(v -> showInputScreen());
        header.addView(backButton, new LinearLayout.LayoutParams(dp(142), dp(46)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(12), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Verification Result");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleBlock.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Receipt verification page");
        subtitle.setTextColor(Color.rgb(184, 219, 215));
        subtitle.setTextSize(12);
        titleBlock.addView(subtitle);

        header.addView(titleBlock, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        return header;
    }

    private View buildForm() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(241, 247, 247));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(18), dp(16), dp(16));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(18));
        form.setBackground(makeRoundRect(Color.WHITE, Color.rgb(229, 238, 239), dp(16)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            form.setElevation(dp(3));
        }

        ptidInput = makeInput("PTID (8 digits)");
        codeInput = makeInput("Verification code (8 or 10 digits)");

        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateValidationState(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        ptidInput.addTextChangedListener(watcher);
        codeInput.addTextChangedListener(watcher);

        verifyButton = new Button(this);
        verifyButton.setText("Verify Receipt");
        verifyButton.setTextSize(16);
        verifyButton.setTypeface(Typeface.DEFAULT_BOLD);
        verifyButton.setAllCaps(false);
        verifyButton.setOnClickListener(v -> verifyReceipt());

        TextView formTitle = new TextView(this);
        formTitle.setText("Enter receipt details");
        formTitle.setTextColor(deepNavy);
        formTitle.setTextSize(18);
        formTitle.setTypeface(Typeface.DEFAULT_BOLD);
        form.addView(formTitle);

        TextView formHint = new TextView(this);
        formHint.setText("Paste a full verification link, or enter the numbers manually.");
        formHint.setTextColor(muted);
        formHint.setTextSize(13);
        formHint.setPadding(0, dp(4), 0, dp(14));
        form.addView(formHint);

        form.addView(makeField(ptidInput, text -> text.matches("\\d{8}")));
        form.addView(makeField(codeInput, text -> text.matches("\\d{8}|\\d{10}")));
        form.addView(verifyButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.setMargins(0, dp(12), 0, 0);
        form.addView(progressBar, progressParams);

        statusText = new TextView(this);
        statusText.setText("Enter valid receipt details to continue.");
        statusText.setTextColor(muted);
        statusText.setTextSize(13);
        statusText.setPadding(0, dp(10), 0, 0);
        form.addView(statusText);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        page.addView(form, cardParams);
        return scroll;
    }

    private View makeField(EditText input, FieldValidator validator) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wrapperParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        wrapperParams.setMargins(0, 0, 0, dp(14));
        wrapper.setLayoutParams(wrapperParams);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        setInputRowBackground(inputRow, false, false);

        TextView stateBadge = new TextView(this);
        stateBadge.setGravity(Gravity.CENTER);
        stateBadge.setTextSize(12);
        stateBadge.setTypeface(Typeface.DEFAULT_BOLD);
        stateBadge.setVisibility(View.GONE);

        TextView clearButton = new TextView(this);
        clearButton.setText("X");
        clearButton.setTextColor(muted);
        clearButton.setTextSize(16);
        clearButton.setTypeface(Typeface.DEFAULT_BOLD);
        clearButton.setGravity(Gravity.CENTER);
        clearButton.setBackground(makeRoundRect(Color.TRANSPARENT, Color.TRANSPARENT, dp(16)));
        clearButton.setVisibility(input.getText().length() > 0 ? View.VISIBLE : View.INVISIBLE);
        clearButton.setContentDescription("Clear field");
        clearButton.setOnClickListener(v -> {
            input.setText("");
            input.requestFocus();
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        clearButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                clearButton.setTextColor(teal);
                clearButton.setBackground(makeRoundRect(Color.rgb(226, 248, 245), Color.TRANSPARENT, dp(16)));
            } else if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
                clearButton.setTextColor(muted);
                clearButton.setBackground(makeRoundRect(Color.TRANSPARENT, Color.TRANSPARENT, dp(16)));
            }
            return false;
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.INVISIBLE);
                updateFieldBadge(stateBadge, s.toString(), validator);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        input.setOnFocusChangeListener((v, hasFocus) -> setInputRowBackground(inputRow, hasFocus, false));

        inputRow.addView(input);
        inputRow.addView(stateBadge, new LinearLayout.LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT));
        inputRow.addView(clearButton, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
        wrapper.addView(inputRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        ));
        return wrapper;
    }

    private EditText makeInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setTextSize(16);
        input.setTextColor(deepNavy);
        input.setHintTextColor(muted);
        input.setPadding(dp(12), 0, dp(6), 0);
        input.setBackgroundColor(Color.TRANSPARENT);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setCursorVisible(true);
        input.setOnClickListener(v -> {
            input.requestFocus();
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) {
                keyboard.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
        );
        input.setLayoutParams(params);
        return input;
    }

    private void updateValidationState() {
        if (verifyButton == null || statusText == null) return;
        parsePastedVerificationText(ptidInput.getText().toString());
        parsePastedVerificationText(codeInput.getText().toString());
        enforceNumericLimit(ptidInput, 8);
        enforceNumericLimit(codeInput, 10);

        String ptid = ptidInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();
        boolean validPtid = ptid.matches("\\d{8}");
        boolean validCode = code.matches("\\d{8}|\\d{10}");
        boolean isValid = validPtid && validCode;

        verifyButton.setEnabled(isValid);
        setVerifyButtonState(isValid);

        if (ptid.isEmpty() && code.isEmpty()) {
            statusText.setText("Enter PTID and verification code.");
            statusText.setTextColor(muted);
        } else if (!validPtid) {
            statusText.setText("PTID must be exactly 8 digits.");
            statusText.setTextColor(danger);
        } else if (!validCode) {
            statusText.setText("Verification code must be 8 or 10 digits.");
            statusText.setTextColor(danger);
        } else {
            statusText.setText("Ready to verify.");
            statusText.setTextColor(teal);
        }
    }

    private void verifyReceipt() {
        String ptid = ptidInput.getText().toString().trim();
        String code = codeInput.getText().toString().trim();

        if (!ptid.matches("\\d{8}") || !code.matches("\\d{8}|\\d{10}")) {
            updateValidationState();
            return;
        }

        if (!isOnline()) {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show();
            statusText.setText("No internet connection. Connect and try again.");
            statusText.setTextColor(danger);
            return;
        }

        try {
            String url = VERIFY_BASE_URL
                    + "?ptid=" + URLEncoder.encode(ptid, "UTF-8")
                    + "&vc=" + URLEncoder.encode(code, "UTF-8");
            showResultScreen();
            webView.loadUrl(url);
        } catch (Exception ex) {
            Toast.makeText(this, "Could not build verification URL", Toast.LENGTH_SHORT).show();
        }
    }

    private void showResultScreen() {
        hideKeyboard();
        webView.setVisibility(View.GONE);
        resultProgressBar.setVisibility(View.VISIBLE);
        resultStatusText.setVisibility(View.VISIBLE);
        resultStatusText.setText("Verifying receipt...");
        resultStatusText.setTextColor(muted);
        inputScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.VISIBLE);
    }

    private void showInputScreen() {
        resultScreen.setVisibility(View.GONE);
        inputScreen.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        resultProgressBar.setVisibility(View.GONE);
        statusText.setText("Ready to verify.");
        statusText.setTextColor(teal);
    }

    private void parsePastedVerificationText(String text) {
        if (applyingParsedInput || text == null) return;
        if (!text.contains("ptid=") && !text.contains("vc=")) return;

        Matcher ptidMatcher = Pattern.compile("(?:ptid=)(\\d{8})", Pattern.CASE_INSENSITIVE).matcher(text);
        Matcher codeMatcher = Pattern.compile("(?:vc=)(\\d{8}|\\d{10})", Pattern.CASE_INSENSITIVE).matcher(text);
        if (!ptidMatcher.find() || !codeMatcher.find()) return;

        applyingParsedInput = true;
        ptidInput.setText(ptidMatcher.group(1));
        ptidInput.setSelection(ptidInput.getText().length());
        codeInput.setText(codeMatcher.group(1));
        codeInput.setSelection(codeInput.getText().length());
        applyingParsedInput = false;
    }

    private void enforceNumericLimit(EditText input, int maxDigits) {
        if (applyingParsedInput) return;

        String current = input.getText().toString();
        String digits = current.replaceAll("\\D", "");
        if (digits.length() > maxDigits) {
            digits = digits.substring(0, maxDigits);
        }
        if (current.equals(digits)) return;

        applyingParsedInput = true;
        input.setText(digits);
        input.setSelection(input.getText().length());
        applyingParsedInput = false;
    }

    private void updateFieldBadge(TextView badge, String value, FieldValidator validator) {
        if (value.isEmpty()) {
            badge.setVisibility(View.GONE);
            return;
        }
        boolean valid = validator.isValid(value.trim());
        badge.setVisibility(View.VISIBLE);
        badge.setText(valid ? "OK" : "!");
        badge.setTextColor(valid ? teal : danger);
    }

    private void setVerifyButtonState(boolean enabled) {
        verifyButton.setTextColor(enabled ? Color.WHITE : Color.rgb(126, 141, 150));
        verifyButton.setBackground(makeRoundRect(
                enabled ? teal : Color.rgb(226, 233, 236),
                enabled ? teal : Color.rgb(211, 222, 226),
                dp(12)
        ));
    }

    private void setInputRowBackground(View view, boolean focused, boolean invalid) {
        int stroke = invalid ? danger : (focused ? teal : border);
        view.setBackground(makeRoundRect(Color.rgb(247, 250, 250), stroke, dp(12)));
    }

    private GradientDrawable makeRoundRect(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (stroke != Color.TRANSPARENT) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private void hideKeyboard() {
        View current = getCurrentFocus();
        if (current == null) return;
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(current.getWindowToken(), 0);
        }
    }

    private boolean isOnline() {
        ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return true;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = manager.getActiveNetwork();
                if (network == null) return false;
                NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }

            NetworkInfo info = manager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (SecurityException ex) {
            return true;
        }
    }

    @Override
    public void onBackPressed() {
        if (resultScreen != null && resultScreen.getVisibility() == View.VISIBLE) {
            showInputScreen();
        } else {
            super.onBackPressed();
        }
    }

    private LinearLayout.LayoutParams matchParent() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private interface FieldValidator {
        boolean isValid(String value);
    }
}

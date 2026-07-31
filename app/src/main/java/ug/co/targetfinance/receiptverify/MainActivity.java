package ug.co.targetfinance.receiptverify;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class MainActivity extends Activity {
    private static final String VERIFY_BASE_URL = "https://targetfinance.co.ug/verify.php";
    private static final int CAMERA_PERMISSION_REQUEST = 24;

    private final int navy = Color.rgb(13, 43, 50);
    private final int deepNavy = Color.rgb(7, 25, 29);
    private final int teal = Color.rgb(0, 198, 155);
    private final int muted = Color.rgb(92, 111, 122);
    private final int danger = Color.rgb(200, 35, 51);
    private final int border = Color.rgb(207, 222, 224);
    private boolean applyingParsedInput = false;

    private EditText ptidInput;
    private EditText codeInput;
    private Button scanButton;
    private Button verifyButton;
    private LinearLayout inputScreen;
    private LinearLayout resultScreen;
    private LinearLayout scannerScreen;
    private TextView statusText;
    private TextView scannerStatusText;
    private ProgressBar progressBar;
    private ProgressBar resultProgressBar;
    private TextView resultStatusText;
    private TextView ptidStateBadge;
    private TextView codeStateBadge;
    private SurfaceView qrPreview;
    private WebView webView;
    private Camera camera;
    private MultiFormatReader qrReader;
    private ToneGenerator toneGenerator;
    private boolean qrScanActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
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

        scannerScreen = new LinearLayout(this);
        scannerScreen.setOrientation(LinearLayout.VERTICAL);
        scannerScreen.setLayoutParams(matchParent());
        scannerScreen.setVisibility(View.GONE);
        scannerScreen.addView(buildScannerHeader());
        qrPreview = new SurfaceView(this);
        qrPreview.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (qrScanActive) {
                    startCameraPreview(holder);
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                if (qrScanActive) {
                    startCameraPreview(holder);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                stopCameraPreview();
            }
        });
        scannerScreen.addView(qrPreview, new LinearLayout.LayoutParams(
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
        root.addView(scannerScreen, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(18));
        header.setBackgroundColor(navy);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.target_finance_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(122), dp(60));
        logoParams.setMargins(0, 0, dp(14), 0);
        header.addView(logo, logoParams);

        TextView title = new TextView(this);
        title.setText("Receipt\nVerification");
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLineSpacing(0, 0.96f);
        title.setIncludeFontPadding(false);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

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

    private View buildScannerHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(12), dp(12), dp(12));
        header.setBackgroundColor(navy);

        Button cancelButton = new Button(this);
        cancelButton.setText("Cancel");
        cancelButton.setAllCaps(false);
        cancelButton.setTextColor(Color.WHITE);
        cancelButton.setTextSize(14);
        cancelButton.setTypeface(Typeface.DEFAULT_BOLD);
        cancelButton.setBackground(makeRoundRect(Color.rgb(20, 70, 78), Color.rgb(20, 70, 78), dp(10)));
        cancelButton.setOnClickListener(v -> {
            stopCameraPreview();
            showInputScreen();
        });
        header.addView(cancelButton, new LinearLayout.LayoutParams(dp(104), dp(46)));

        scannerStatusText = new TextView(this);
        scannerStatusText.setText("Point camera at receipt QR code");
        scannerStatusText.setTextColor(Color.WHITE);
        scannerStatusText.setTextSize(15);
        scannerStatusText.setTypeface(Typeface.DEFAULT_BOLD);
        scannerStatusText.setPadding(dp(12), 0, 0, 0);
        header.addView(scannerStatusText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        return header;
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

        scanButton = new Button(this);
        scanButton.setText("Scan QR Code");
        scanButton.setTextSize(15);
        scanButton.setTypeface(Typeface.DEFAULT_BOLD);
        scanButton.setAllCaps(false);
        scanButton.setTextColor(teal);
        scanButton.setBackground(makeRoundRect(Color.rgb(236, 251, 248), teal, dp(12)));
        scanButton.setOnClickListener(v -> startQrScan());

        TextView formTitle = new TextView(this);
        formTitle.setText("Enter receipt details");
        formTitle.setTextColor(deepNavy);
        formTitle.setTextSize(18);
        formTitle.setTypeface(Typeface.DEFAULT_BOLD);
        formTitle.setPadding(0, 0, 0, dp(14));
        form.addView(formTitle);

        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        scanParams.setMargins(0, 0, 0, dp(14));
        form.addView(scanButton, scanParams);

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
        if (input == ptidInput) {
            ptidStateBadge = stateBadge;
        } else if (input == codeInput) {
            codeStateBadge = stateBadge;
        }

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
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                input.post(() -> {
                    String finalValue = input.getText().toString();
                    clearButton.setVisibility(finalValue.length() > 0 ? View.VISIBLE : View.INVISIBLE);
                    updateFieldBadge(stateBadge, finalValue, validator);
                });
            }
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
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
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
        updateFieldBadge(ptidStateBadge, ptid, text -> text.matches("\\d{8}"));
        updateFieldBadge(codeStateBadge, code, text -> text.matches("\\d{8}|\\d{10}"));
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
        stopCameraPreview();
        webView.setVisibility(View.GONE);
        resultProgressBar.setVisibility(View.VISIBLE);
        resultStatusText.setVisibility(View.VISIBLE);
        resultStatusText.setText("Verifying receipt...");
        resultStatusText.setTextColor(muted);
        inputScreen.setVisibility(View.GONE);
        scannerScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.VISIBLE);
    }

    private void showInputScreen() {
        stopCameraPreview();
        resultScreen.setVisibility(View.GONE);
        scannerScreen.setVisibility(View.GONE);
        inputScreen.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        resultProgressBar.setVisibility(View.GONE);
        statusText.setText("Ready to verify.");
        statusText.setTextColor(teal);
    }

    private void startQrScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
            return;
        }
        showScannerScreen();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showScannerScreen();
            } else {
                showQrMessage("Camera permission is needed to scan QR codes.", danger);
            }
        }
    }

    private void showScannerScreen() {
        hideKeyboard();
        inputScreen.setVisibility(View.GONE);
        resultScreen.setVisibility(View.GONE);
        scannerScreen.setVisibility(View.VISIBLE);
        scannerStatusText.setText("Point camera at receipt QR code");
        qrScanActive = true;
        configureQrReader();
        SurfaceHolder holder = qrPreview.getHolder();
        if (holder.getSurface() != null && holder.getSurface().isValid()) {
            startCameraPreview(holder);
        }
    }

    private void configureQrReader() {
        qrReader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Collections.singletonList(BarcodeFormat.QR_CODE));
        qrReader.setHints(hints);
    }

    private void startCameraPreview(SurfaceHolder holder) {
        stopCameraPreview();
        qrScanActive = true;
        try {
            camera = Camera.open();
            configureCamera(camera);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback((data, activeCamera) -> decodePreviewFrame(data, activeCamera));
            camera.startPreview();
            requestQrFocus();
        } catch (Exception ex) {
            stopCameraPreview();
            scannerStatusText.setText("Could not start camera.");
            showQrMessage("Could not start camera. Check permission and try again.", danger);
        }
    }

    private void configureCamera(Camera activeCamera) {
        Camera.Parameters parameters = activeCamera.getParameters();
        Camera.Size previewSize = choosePreviewSize(parameters.getSupportedPreviewSizes());
        if (previewSize != null) {
            parameters.setPreviewSize(previewSize.width, previewSize.height);
        }

        List<String> focusModes = parameters.getSupportedFocusModes();
        if (focusModes != null) {
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
            }
        }

        List<String> sceneModes = parameters.getSupportedSceneModes();
        if (sceneModes != null && sceneModes.contains(Camera.Parameters.SCENE_MODE_BARCODE)) {
            parameters.setSceneMode(Camera.Parameters.SCENE_MODE_BARCODE);
        }

        List<String> whiteBalanceModes = parameters.getSupportedWhiteBalance();
        if (whiteBalanceModes != null && whiteBalanceModes.contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        }

        activeCamera.setParameters(parameters);
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> supportedSizes) {
        if (supportedSizes == null || supportedSizes.isEmpty()) return null;

        Camera.Size best = null;
        for (Camera.Size size : supportedSizes) {
            int longSide = Math.max(size.width, size.height);
            int shortSide = Math.min(size.width, size.height);
            if (longSide > 1920 || shortSide > 1080) continue;
            if (best == null || size.width * size.height > best.width * best.height) {
                best = size;
            }
        }
        return best != null ? best : supportedSizes.get(0);
    }

    private void requestQrFocus() {
        if (!qrScanActive || camera == null) return;

        try {
            String focusMode = camera.getParameters().getFocusMode();
            if (Camera.Parameters.FOCUS_MODE_AUTO.equals(focusMode)
                    || Camera.Parameters.FOCUS_MODE_MACRO.equals(focusMode)) {
                camera.autoFocus((success, activeCamera) -> {
                    if (qrScanActive && qrPreview != null) {
                        qrPreview.postDelayed(this::requestQrFocus, 1200);
                    }
                });
            }
        } catch (Exception ignored) {
        }
    }

    private void stopCameraPreview() {
        qrScanActive = false;
        if (camera == null) return;
        try {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
        } catch (Exception ignored) {
        }
        camera = null;
    }

    private void decodePreviewFrame(byte[] data, Camera activeCamera) {
        if (!qrScanActive || qrReader == null || activeCamera == null) return;

        Camera.Size size = activeCamera.getParameters().getPreviewSize();
        int width = size.width;
        int height = size.height;
        byte[] rotatedData = rotatePreviewData(data, width, height);
        int rotatedWidth = height;
        int rotatedHeight = width;

        PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                rotatedData,
                rotatedWidth,
                rotatedHeight,
                0,
                0,
                rotatedWidth,
                rotatedHeight,
                false
        );
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = qrReader.decodeWithState(bitmap);
            qrScanActive = false;
            runOnUiThread(() -> {
                stopCameraPreview();
                showInputScreen();
                handleQrScanResult(result.getText());
            });
        } catch (NotFoundException ex) {
            qrReader.reset();
        }
    }

    private byte[] rotatePreviewData(byte[] data, int width, int height) {
        byte[] rotated = new byte[width * height];
        int index = 0;
        for (int x = 0; x < width; x++) {
            for (int y = height - 1; y >= 0; y--) {
                rotated[index++] = data[y * width + x];
            }
        }
        return rotated;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scannerScreen != null && scannerScreen.getVisibility() == View.VISIBLE) {
            stopCameraPreview();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (scannerScreen != null && scannerScreen.getVisibility() == View.VISIBLE && !qrScanActive) {
            qrScanActive = true;
            configureQrReader();
            SurfaceHolder holder = qrPreview.getHolder();
            if (holder.getSurface() != null && holder.getSurface().isValid()) {
                startCameraPreview(holder);
            }
            return;
        }
    }

    private void handleQrScanResult(String qrText) {
        if (qrText == null || qrText.trim().isEmpty()) {
            showQrMessage("Scan cancelled.", muted);
            return;
        }

        ParsedReceipt parsedReceipt = parseReceiptUrl(qrText.trim());
        if (!parsedReceipt.isValid) {
            showQrMessage(parsedReceipt.message, danger);
            return;
        }

        applyingParsedInput = true;
        ptidInput.setText(parsedReceipt.ptid);
        ptidInput.setSelection(ptidInput.getText().length());
        codeInput.setText(parsedReceipt.code);
        codeInput.setSelection(codeInput.getText().length());
        applyingParsedInput = false;

        updateValidationState();
        playScanSuccessBeep();
        showQrMessage("QR code scanned. Review details, then verify.", teal);
    }

    private void playScanSuccessBeep() {
        if (toneGenerator == null) return;
        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 160);
    }

    private ParsedReceipt parseReceiptUrl(String qrText) {
        Uri uri = Uri.parse(qrText);
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getPath();

        boolean validEndpoint = "https".equalsIgnoreCase(scheme)
                && "targetfinance.co.ug".equalsIgnoreCase(host)
                && "/verify.php".equals(path);
        if (!validEndpoint) {
            return ParsedReceipt.error("This QR code is not a valid Target Finance receipt.");
        }

        String ptid = uri.getQueryParameter("ptid");
        String code = uri.getQueryParameter("vc");
        if (ptid == null || code == null || ptid.isEmpty() || code.isEmpty()) {
            return ParsedReceipt.error("QR code found, but PTID or verification code is missing.");
        }
        if (!ptid.matches("\\d{8}") || !code.matches("\\d{8}|\\d{10}")) {
            return ParsedReceipt.error("QR code found, but receipt numbers are not in the expected format.");
        }

        return ParsedReceipt.success(ptid, code);
    }

    private void showQrMessage(String message, int color) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        statusText.setText(message);
        statusText.setTextColor(color);
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
        if (badge == null) return;
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
        if (scannerScreen != null && scannerScreen.getVisibility() == View.VISIBLE) {
            stopCameraPreview();
            showInputScreen();
        } else if (resultScreen != null && resultScreen.getVisibility() == View.VISIBLE) {
            showInputScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
        super.onDestroy();
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

    private static class ParsedReceipt {
        final boolean isValid;
        final String ptid;
        final String code;
        final String message;

        private ParsedReceipt(boolean isValid, String ptid, String code, String message) {
            this.isValid = isValid;
            this.ptid = ptid;
            this.code = code;
            this.message = message;
        }

        static ParsedReceipt success(String ptid, String code) {
            return new ParsedReceipt(true, ptid, code, "");
        }

        static ParsedReceipt error(String message) {
            return new ParsedReceipt(false, "", "", message);
        }
    }
}

package com.example.morrassignment;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.morrassignment.utils.CardType;
import com.example.morrassignment.utils.ValidationUtils;
import com.jakewharton.rxbinding2.view.RxView;
import com.jakewharton.rxbinding2.widget.RxTextView;

import java.util.Arrays;
import java.util.regex.Pattern;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import mostafa.ma.saleh.gmail.com.editcredit.EditCredit;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private EditCredit creditCardNumberView;
    private EditText creditCardCvcView;
    private ImageView creditCardType;
    private EditText creditCardExpirationDateView;
    private TextView errorText;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resolveViews();

        submitButton.setOnClickListener((view) -> {
            Log.d(TAG, "Submit");
            findViewById(R.id.container).requestFocus();
        });

        // Create source Observables
        final Observable<String> creditCardNumber =
                RxTextView.textChanges(creditCardNumberView)
                        .map(CharSequence::toString);

        final Observable<String> creditCardCvc =
                RxTextView.textChanges(creditCardCvcView)
                        .map(CharSequence::toString);

        final Observable<String> creditCardExpirationDate =
                RxTextView.textChanges(creditCardExpirationDateView)
                        .map(CharSequence::toString);

        // Create derived Observables
        final Observable<CardType> cardType =
                creditCardNumber
                        .map(CardType::fromNumber);

        final Observable<Boolean> isKnownCardType =
                cardType
                        .map(cardTypeValue -> cardTypeValue != CardType.UNKNOWN);

        final Observable<Boolean> isValidCheckSum =
                creditCardNumber
                        .map(ValidationUtils::checkCardChecksum);

        final Observable<Boolean> isValidNumber =
                Observable.combineLatest(
                        isKnownCardType,
                        isValidCheckSum,
                        (isValidType, isChecksumCorrect) -> isValidType && isChecksumCorrect);

        final Observable<Boolean> isValidCvc =
                Observable.combineLatest(
                        cardType,
                        creditCardCvc,
                        ValidationUtils::isValidCvc);

        Pattern expirationDatePattern = Pattern.compile("^\\d\\d/\\d\\d$");
        final Observable<Boolean> isValidExpirationDate =
                creditCardExpirationDate
                        .map(text -> expirationDatePattern.matcher(text).find());


        // Show output in the UI

        getSubmitButtonEnabled(isValidNumber, isValidCvc, isValidExpirationDate)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(submitButton::setEnabled);

        getErrorText(isKnownCardType, isValidCheckSum, isValidCvc, isValidExpirationDate)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(errorText::setText);

        showErrorForEditText(creditCardNumberView, isValidNumber);
        showErrorForEditText(creditCardCvcView, isValidCvc);
        showErrorForEditText(creditCardExpirationDateView, isValidExpirationDate);
    }

    private static Observable<Boolean> getSubmitButtonEnabled(
            Observable<Boolean> isValidNumber,
            Observable<Boolean> isValidCvc,
            Observable<Boolean> isValidExpirationDate) {
        return Observable.combineLatest(
                isValidNumber, isValidCvc, isValidExpirationDate,
                (isValidNumberValue, isValidCvcValue, isValidExpirationDateValue) ->
                        isValidNumberValue && isValidCvcValue && isValidExpirationDateValue);
    }

    private static Observable<String> getErrorText(Observable<Boolean> isKnownCardType,
                                                   Observable<Boolean> isValidCheckSum,
                                                   Observable<Boolean> isValidCvc,
                                                   Observable<Boolean> isValidExpirationDate) {
        return Observable.combineLatest(
                Arrays.asList(
                        isKnownCardType.map(value -> value ? "" : "Unknown card type"),
                        isValidCheckSum.map(value -> value ? "" : "Invalid checksum"),
                        isValidCvc.map(value -> value ? "" : "Invalid CVC code"),
                        isValidExpirationDate.map(value -> value ? "" : "Invalid expiration date")),
                (errorStrings) -> {
                    StringBuilder builder = new StringBuilder();
                    for (Object errorString : errorStrings) {
                        if (!"".equals(errorString)) {
                            builder.append(errorString);
                            builder.append("\n");
                        }
                    }
                    return builder.toString();
                });
    }

    private static void showErrorForEditText(EditText editText,
                                             Observable<Boolean> isValid) {
        getShowErrorForEditText(editText, isValid)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(value -> editText.setTextColor(
                        value ? Color.RED : Color.BLACK));
    }

    private static Observable<Boolean> getShowErrorForEditText(EditText editText,
                                                               Observable<Boolean> isValid) {
        // We need refCount because we have two subscribers: otherwise the first will be
        // unsubscribed automatically when second one arrives
        final Observable<Boolean> hasFocus = RxView.focusChanges(editText).publish().refCount();

        final Observable<Boolean> hasHadFocus =
                Observable.concat(
                        Observable.just(false),
                        hasFocus.filter(value -> value).firstElement().toObservable());

        return Observable.combineLatest(
                hasHadFocus, hasFocus, isValid,
                (hasHadFocusValue, hasFocusValue, isValidValue) ->
                        hasHadFocusValue && (!hasFocusValue && !isValidValue));
    }

    private void resolveViews() {
        creditCardNumberView = (EditCredit) findViewById(R.id.credit_card_number);
        creditCardCvcView = (EditText) findViewById(R.id.credit_card_cvc);

        creditCardExpirationDateView = (EditText) findViewById(R.id.expiration_date);
        errorText = (TextView) findViewById(R.id.error_text);
        submitButton = (Button) findViewById(R.id.submit_button);
    }
}

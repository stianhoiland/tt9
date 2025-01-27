package io.github.sspanak.tt9.ime;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import io.github.sspanak.tt9.ime.helpers.Key;
import io.github.sspanak.tt9.preferences.SettingsStore;


abstract class KeyPadHandler extends InputMethodService {
	protected InputConnection currentInputConnection = null;

	protected SettingsStore settings;

	// temporal key handling
	private boolean isBackspaceHandled = false;

	private int ignoreNextKeyUp = 0;

	private int lastKeyCode = 0;
	private int keyRepeatCounter = 0;

	private int lastNumKeyCode = 0;
	private int numKeyRepeatCounter = 0;


	/**
	 * Main initialization of the input method component. Be sure to call to
	 * super class.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		settings = new SettingsStore(getApplicationContext());

		onInit();
	}


	@Override
	public boolean onEvaluateInputViewShown() {
		super.onEvaluateInputViewShown();
		onRestart(getCurrentInputEditorInfo());
		return shouldBeVisible();
	}


	@Override
	public boolean onEvaluateFullscreenMode() {
		return false;
	}


	/**
	 * Called by the framework when your view for creating input needs to be
	 * generated. This will be called the first time your input method is
	 * displayed, and every time it needs to be re-created such as due to a
	 * configuration change.
	 */
	@Override
	public View onCreateInputView() {
		return createSoftKeyView();
	}


	/**
	 * This is the main point where we do our initialization of the input method
	 * to begin operating on an application. At this point we have been bound to
	 * the client, and are now receiving all of the detailed information about
	 * the target of our edits.
	 */
	@Override
	public void onStartInput(EditorInfo inputField, boolean restarting) {
		currentInputConnection = getCurrentInputConnection();
		// Logger.d("T9.onStartInput", "inputType: " + inputField.inputType + " fieldId: " + inputField.fieldId + " fieldName: " + inputField.fieldName + " packageName: " + inputField.packageName);
		onStart(inputField);
	}


	@Override
	public void onStartInputView(EditorInfo inputField, boolean restarting) {
		currentInputConnection = getCurrentInputConnection();
		onRestart(inputField);
	}


	@Override
	public void onFinishInputView(boolean finishingInput) {
		super.onFinishInputView(finishingInput);
		onFinishTyping();
	}

	/**
	 * This is called when the user is done editing a field. We can use this to
	 * reset our state.
	 */
	@Override
	public void onFinishInput() {
		super.onFinishInput();
		// Logger.d("onFinishInput", "When is this called?");
		onStop();
	}


	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (shouldBeOff()) {
			return false;
		}

//		Logger.d("onKeyDown", "Key: " + event + " repeat?: " + event.getRepeatCount() + " long-time: " + event.isLongPress());

		// "backspace" key must repeat its function when held down, so we handle it in a special way
		if (Key.isBackspace(settings, keyCode)) {
			isBackspaceHandled = onBackspace();
			return isBackspaceHandled;
		} else {
			isBackspaceHandled = false;
		}

		// start tracking key hold
		if (Key.isNumber(keyCode) || Key.isHotkey(settings, -keyCode)) {
			event.startTracking();
		}

		if (Key.isArrow(keyCode)) {
			boolean shouldIgnore = shouldIgnoreKeyDown(keyCode, event);
			if (shouldIgnore) {
				return super.onKeyDown(keyCode, event);
			} else {
				return onArrow(keyCode, keyRepeatCounter > 0);
			}
		}

		return Key.isNumber(keyCode)
			|| Key.isOK(keyCode)
			|| Key.isHotkey(settings, keyCode) || Key.isHotkey(settings, -keyCode) // press or hold a hotkey
			|| (keyCode == KeyEvent.KEYCODE_POUND && onText("#"))
			|| (keyCode == KeyEvent.KEYCODE_STAR && onText("*"))
			|| super.onKeyDown(keyCode, event); // let the system handle the keys we don't care about (usually, only: KEYCODE_BACK)
	}


	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (shouldBeOff()) {
			return false;
		}

//		Logger.d("onLongPress", "LONG PRESS: " + keyCode);

		if (event.getRepeatCount() > 1) {
			return true;
		}

		ignoreNextKeyUp = keyCode;
		if (Key.isNumber(keyCode)) {
			numKeyRepeatCounter = 0;
			lastNumKeyCode = 0;
		} else {
			keyRepeatCounter = 0;
			lastKeyCode = 0;
		}

		if (handleHotkey(keyCode, true)) {
			return true;
		}

		if (Key.isNumber(keyCode)) {
			return onNumber(Key.codeToNumber(settings, keyCode), true, 0);
		}

		ignoreNextKeyUp = 0;
		return false;
	}


	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (shouldBeOff()) {
			return false;
		}

		//		Logger.d("onKeyUp", "Key: " + keyCode + " repeat?: " + event.getRepeatCount());

		if (keyCode == ignoreNextKeyUp) {
//			Logger.d("onKeyUp", "Ignored: " + keyCode);
			ignoreNextKeyUp = 0;
			return true;
		}

		// repeat handling
		keyRepeatCounter = (lastKeyCode == keyCode) ? keyRepeatCounter + 1 : 0;
		lastKeyCode = keyCode;

		if (Key.isNumber(keyCode)) {
			numKeyRepeatCounter = (lastNumKeyCode == keyCode) ? numKeyRepeatCounter + 1 : 0;
			lastNumKeyCode = keyCode;
		}

		// backspace is handled in onKeyDown only, so we ignore it here
		if (isBackspaceHandled) {
			return true;
		}

		if (Key.isNumber(keyCode)) {
			return onNumber(Key.codeToNumber(settings, keyCode), false, numKeyRepeatCounter);
		}

		if (Key.isOK(keyCode)) {
			return onOK();
		}

		if (handleHotkey(keyCode, false)) {
			return true;
		}

		// let the system handle the keys we don't care about (usually, only: KEYCODE_BACK)
		return super.onKeyUp(keyCode, event);
	}


	private boolean handleHotkey(int keyCode, boolean hold) {
		if (keyCode == settings.getKeyAddWord() * (hold ? -1 : 1)) {
			return onKeyAddWord();
		}

		if (keyCode == settings.getKeyNextLanguage() * (hold ? -1 : 1)) {
			return onKeyNextLanguage();
		}

		if (keyCode == settings.getKeyNextInputMode() * (hold ? -1 : 1)) {
			return onKeyNextInputMode();
		}

		if (keyCode == settings.getKeyShowSettings() * (hold ? -1 : 1)) {
			return onKeyShowSettings();
		}

		return false;
	}


	protected void resetKeyRepeat() {
		numKeyRepeatCounter = 0;
		keyRepeatCounter = 0;
		lastNumKeyCode = 0;
		lastKeyCode = 0;
	}


	// hardware key handlers
	abstract protected boolean onArrow(int key, boolean repeat);
	abstract public boolean onBackspace();
	abstract protected boolean onNumber(int key, boolean hold, int repeat);
	abstract public boolean onOK();
	abstract public boolean onText(String text); // used for "#", "*" and whatnot

	// hotkey handlers
	abstract protected boolean onKeyAddWord();
	abstract protected boolean onKeyNextLanguage();
	abstract protected boolean onKeyNextInputMode();
	abstract protected boolean onKeyShowSettings();

	// helpers
	abstract protected void onInit();
	abstract protected void onStart(EditorInfo inputField);
	abstract protected void onRestart(EditorInfo inputField);
	abstract protected void onFinishTyping();
	abstract protected void onStop();

	// UI
	abstract protected View createSoftKeyView();
	abstract protected boolean shouldBeVisible();
	abstract protected boolean shouldBeOff();
	abstract protected boolean shouldIgnoreKeyDown(int keyCode, KeyEvent event);
}

package com.mparticle.kits;

import android.app.Application;
import android.content.Context;

import com.apptentive.android.sdk.Apptentive;
import com.apptentive.android.sdk.ApptentiveConfiguration;
import com.apptentive.android.sdk.ApptentiveLog;
import com.apptentive.android.sdk.ApptentiveNotifications;
import com.apptentive.android.sdk.conversation.Conversation;
import com.apptentive.android.sdk.model.CommerceExtendedData;
import com.apptentive.android.sdk.model.ExtendedData;
import com.apptentive.android.sdk.notifications.ApptentiveNotification;
import com.apptentive.android.sdk.notifications.ApptentiveNotificationCenter;
import com.apptentive.android.sdk.notifications.ApptentiveNotificationObserver;
import com.mparticle.MPEvent;
import com.mparticle.MParticle;
import com.mparticle.commerce.CommerceEvent;
import com.mparticle.commerce.Product;
import com.mparticle.commerce.TransactionAttributes;
import com.mparticle.identity.MParticleUser;

import org.json.JSONException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.mparticle.kits.CustomDataParser.parseValue;
import static com.mparticle.kits.StringUtils.tryParseSettingFlag;

public class ApptentiveKit extends KitIntegration implements
		KitIntegration.EventListener,
		KitIntegration.CommerceListener,
		KitIntegration.AttributeListener,
		ApptentiveNotificationObserver {

	private static final String APPTENTIVE_APP_KEY = "apptentiveAppKey";
	private static final String APPTENTIVE_APP_SIGNATURE = "apptentiveAppSignature";
	private static final String ENABLE_TYPE_DETECTION = "enableTypeDetection";

	private static final String SUFFIX_KEY_FLAG = "_flag";
	private static final String SUFFIX_KEY_NUMBER = "_number";

	private boolean enableTypeDetection;
	private String lastKnownFirstName;
	private String lastKnownLastName;

	@Override
	public String getName() {
		return "Apptentive";
	}

	@Override
	protected List<ReportingMessage> onKitCreate(Map<String, String> settings, Context context) {
		String apptentiveAppKey = settings.get(APPTENTIVE_APP_KEY);
		String apptentiveAppSignature = settings.get(APPTENTIVE_APP_SIGNATURE);
		if (KitUtils.isEmpty(apptentiveAppKey)) {
			throw new IllegalArgumentException("Apptentive App Key is required. If you are migrating from a previous version, you may need to enter the new Apptentive App Key and Signature on the mParticle website.");
		}
		if (KitUtils.isEmpty(apptentiveAppSignature)) {
			throw new IllegalArgumentException("Apptentive App Signature is required. If you are migrating from a previous version, you may need to enter the new Apptentive App Key and Signature on the mParticle website.");
		}

		enableTypeDetection = tryParseSettingFlag(settings, ENABLE_TYPE_DETECTION, true);

    	ApptentiveConfiguration configuration = new ApptentiveConfiguration(apptentiveAppKey, apptentiveAppSignature);
		Apptentive.register((Application)context.getApplicationContext(), configuration);
		ApptentiveNotificationCenter.defaultCenter()
				.addObserver(ApptentiveNotifications.NOTIFICATION_CONVERSATION_STATE_DID_CHANGE, this);

		return null;
	}

	@Override
	protected void onKitDestroy() {
		super.onKitDestroy();
		ApptentiveNotificationCenter.defaultCenter().removeObserver(this);
	}

	@Override
	public List<ReportingMessage> setOptOut(boolean optedOut) {
		return null;
	}

	@Override
	public void setUserIdentity(MParticle.IdentityType identityType, String id) {
		if (identityType.equals(MParticle.IdentityType.Email)) {
			Apptentive.setPersonEmail(id);
		} else if (identityType.equals(MParticle.IdentityType.CustomerId)) {
			if (KitUtils.isEmpty(Apptentive.getPersonName())) {
				// Use id as customer name iff no full name is set yet.
				Apptentive.setPersonName(id);
			}
		}
	}

	@Override
	public void setUserAttribute(String attributeKey, String attributeValue) {
		if (attributeKey.equalsIgnoreCase(MParticle.UserAttributes.FIRSTNAME)) {
			lastKnownFirstName = attributeValue;
		} else if (attributeKey.equalsIgnoreCase(MParticle.UserAttributes.LASTNAME)) {
			lastKnownLastName = attributeValue;
		} else {
			addCustomPersonData(attributeKey, attributeValue);
			return;
		}

		String fullName = "";
		if (!KitUtils.isEmpty(lastKnownFirstName)) {
			fullName += lastKnownFirstName;
		}
		if (!KitUtils.isEmpty(lastKnownLastName)) {
			if (fullName.length() > 0) { fullName += " "; }
			fullName += lastKnownLastName;
		}
		Apptentive.setPersonName(fullName.trim());
	}

	@Override
	public void setUserAttributeList(String key, List<String> list) {

	}

	@Override
	public boolean supportsAttributeLists() {
		return false;
	}

	@Override
	public void setAllUserAttributes(Map<String, String> attributes, Map<String, List<String>> attributeLists) {
		String firstName = "";
		String lastName = "";
		for (Map.Entry<String, String> entry : attributes.entrySet()){
			if (entry.getKey().equalsIgnoreCase(MParticle.UserAttributes.FIRSTNAME)) {
				firstName = entry.getValue();
			} else if (entry.getKey().equalsIgnoreCase(MParticle.UserAttributes.LASTNAME)) {
				lastName = entry.getValue();
			} else {
				addCustomPersonData(entry.getKey(), entry.getValue());
			}
		}
		String fullName;
		if (!KitUtils.isEmpty(firstName) && !KitUtils.isEmpty(lastName)) {
			fullName = firstName + " " + lastName;
		} else {
			fullName = firstName + lastName;
		}
		Apptentive.setPersonName(fullName.trim());
	}

	@Override
	public void removeUserAttribute(String key) {
		Apptentive.removeCustomPersonData(key);
	}


	@Override
	public void removeUserIdentity(MParticle.IdentityType identityType) {

	}

	@Override
	public List<ReportingMessage> logout() {
		return null;
	}

	@Override
	public List<ReportingMessage> leaveBreadcrumb(String breadcrumb) {
		return null;
	}

	@Override
	public List<ReportingMessage> logError(String message, Map<String, String> errorAttributes) {
		return null;
	}

	@Override
	public List<ReportingMessage> logException(Exception exception, Map<String, String> exceptionAttributes, String message) {
		return null;
	}

	@Override
	public List<ReportingMessage> logEvent(MPEvent event) {
		engage(getContext(), event.getEventName(), event.getInfo());
		List<ReportingMessage> messageList = new LinkedList<ReportingMessage>();
		messageList.add(ReportingMessage.fromEvent(this, event));
		return messageList;
	}

	@Override
	public List<ReportingMessage> logScreen(String screenName, Map<String, String> eventAttributes) {
		engage(getContext(), screenName, eventAttributes);
		List<ReportingMessage> messages = new LinkedList<ReportingMessage>();
		messages.add(new ReportingMessage(this, ReportingMessage.MessageType.SCREEN_VIEW, System.currentTimeMillis(), eventAttributes));
		return messages;
	}

	@Override
	public List<ReportingMessage> logLtvIncrease(BigDecimal valueIncreased, BigDecimal valueTotal, String eventName, Map<String, String> contextInfo) {
		return null;
	}

	@Override
	public List<ReportingMessage> logEvent(CommerceEvent event) {
		if (!KitUtils.isEmpty(event.getProductAction())) {
			try {
				Map<String, String> eventActionAttributes = new HashMap<String, String>();
				CommerceEventUtils.extractActionAttributes(event, eventActionAttributes);

				CommerceExtendedData apptentiveCommerceData = null;

				TransactionAttributes transactionAttributes = event.getTransactionAttributes();
				if (transactionAttributes != null) {
					apptentiveCommerceData = new CommerceExtendedData();

					String transaction_id = transactionAttributes.getId();
					if (!KitUtils.isEmpty(transaction_id)) {
						apptentiveCommerceData.setId(transaction_id);
					}
					Double transRevenue = transactionAttributes.getRevenue();
					if (transRevenue != null) {
						apptentiveCommerceData.setRevenue(transRevenue);
					}
					Double transShipping = transactionAttributes.getShipping();
					if (transShipping != null) {
						apptentiveCommerceData.setShipping(transShipping);
					}
					Double transTax = transactionAttributes.getTax();
					if (transTax != null) {
						apptentiveCommerceData.setTax(transTax);
					}
					String transAffiliation = transactionAttributes.getAffiliation();
					if (!KitUtils.isEmpty(transAffiliation)) {
						apptentiveCommerceData.setAffiliation(transAffiliation);
					}
					String transCurrency = eventActionAttributes.get(CommerceEventUtils.Constants.ATT_ACTION_CURRENCY_CODE);
					if (KitUtils.isEmpty(transCurrency)) {
						transCurrency = CommerceEventUtils.Constants.DEFAULT_CURRENCY_CODE;
					}
					apptentiveCommerceData.setCurrency(transCurrency);

					// Add each item
					List<Product> productList = event.getProducts();
					if (productList != null) {
						for (Product product : productList) {
							CommerceExtendedData.Item item = new CommerceExtendedData.Item();
							item.setId(product.getSku());
							item.setName(product.getName());
							item.setCategory(product.getCategory());
							item.setPrice(product.getUnitPrice());
							item.setQuantity(product.getQuantity());
							item.setCurrency(transCurrency);
							apptentiveCommerceData.addItem(item);
						}
					}
				}


				if (apptentiveCommerceData != null) {
					engage(getContext(),
							String.format("eCommerce - %s", event.getProductAction()),
							event.getCustomAttributes(),
							apptentiveCommerceData
					);
					List<ReportingMessage> messages = new LinkedList<ReportingMessage>();
					messages.add(ReportingMessage.fromEvent(this, event));
					return messages;
				}
			}catch (JSONException jse) {

			}

		}
		return null;
	}

	//region Notifications

	@Override
	public void onReceiveNotification(ApptentiveNotification notification) {
		if (notification.hasName(ApptentiveNotifications.NOTIFICATION_CONVERSATION_STATE_DID_CHANGE)) {
			Conversation conversation = notification.getRequiredUserInfo(ApptentiveNotifications.NOTIFICATION_KEY_CONVERSATION, Conversation.class);
			if (conversation != null && conversation.hasActiveState()) {
				MParticleUser currentUser = getCurrentUser();
				if (currentUser == null) {
					ApptentiveLog.w("Unable to update mParticle id: no current user");
					return;
				}

				String userId = Long.toString(currentUser.getId());
				ApptentiveLog.v("Updating mParticle id: %s", ApptentiveLog.hideIfSanitized(userId));

				conversation.getPerson().setMParticleId(userId);
			}
		}
	}

	//endregion

	//region Helpers

	private void engage(Context context, String event, Map<String, String> customData) {
		engage(context, event, customData, (ExtendedData[]) null);
	}

	private void engage(Context context, String event, Map<String, String> customData, ExtendedData... extendedData) {
		Apptentive.engage(context, event, parseCustomData(customData), extendedData);
	}

	/* Apptentive SDK does not provide a function which accepts Object as custom data so we need to cast */
	private void addCustomPersonData(String key, String value) {
		// original key
		Apptentive.addCustomPersonData(key, value);

		// typed key
		if (enableTypeDetection) {
			final Object typedValue = parseValue(value);
			if (typedValue instanceof String) {
				// the value is already set
			} else if (typedValue instanceof Boolean) {
				Apptentive.addCustomPersonData(key + SUFFIX_KEY_FLAG, (Boolean) typedValue);
			} else if (typedValue instanceof Number) {
				Apptentive.addCustomPersonData(key + SUFFIX_KEY_NUMBER, (Number) typedValue);
			} else {
				ApptentiveLog.e("Unexpected custom person data type: %s", typedValue != null ? typedValue.getClass() : null);
			}
		}
	}

	private Map<String, Object> parseCustomData(Map<String, String> map) {
		if (map != null) {
			final Map<String, Object> res = new HashMap<>();
			for (Map.Entry<String, String> e : map.entrySet()) {
				final String key = e.getKey();
				final String value = e.getValue();

				// original key
				res.put(key, value);

				// typed key
				if (enableTypeDetection) {
					final Object typedValue = parseValue(value);
					if (typedValue instanceof String) {
						// the value is already set
					} else if (typedValue instanceof Boolean) {
						res.put(key + SUFFIX_KEY_FLAG, (Boolean) typedValue);
					} else if (typedValue instanceof Number) {
						res.put(key + SUFFIX_KEY_NUMBER, (Number) typedValue);
					} else {
						ApptentiveLog.e("Unexpected custom data type: %s", typedValue != null ? typedValue.getClass() : null);
					}
				}
			}
			return res;
		}

		return null;
	}

	//endregion
}

var urlPrefix = "http://capitalistparadise.com/nationstates/v1_7/";
var pageUrl = window.location.href;

addStylesheet(urlPrefix + 'nouislider.fox.css');
addStylesheet(urlPrefix + 'bootstrap-button.css');
addStylesheet(urlPrefix + 'two_column.css');
addStylesheet(urlPrefix + 'nationstates++.css');
if (document.head.innerHTML.indexOf("antiquity") != -1) {
	addStylesheet(urlPrefix + 'nationstates++_antiquity.css');
}
addJavascript(urlPrefix + 'nationstates++_common.js');

if (pageUrl.indexOf('http://www.nationstates.net/') > -1 && isSettingEnabled("region_enhancements")) {
	console.log('[NationStates++] Detected NationStates Page. Loading...');

	addJavascript(urlPrefix + 'jquery.caret.js');
	addJavascript(urlPrefix + 'jquery.highlight.js');
	addJavascript(urlPrefix + 'jquery.nouislider.min.js');
	addJavascript(urlPrefix + 'nationstates++.js');
	if (isSettingEnabled("embassy_flags")) {
		addJavascript(urlPrefix + 'embassy_flags.js');
	}

	console.log('[NationStates++] Loading Completed Successfully.');
} else if (pageUrl.indexOf('http://forum.nationstates.net/') > -1 ) {
	console.log('[NationStates++] Detected NationStates Forum Page. Loading...');
	//forums do not have jQuery
	addJavascript("//ajax.googleapis.com/ajax/libs/jquery/1.9.0/jquery.min.js");
	addJavascript("//ajax.googleapis.com/ajax/libs/jqueryui/1.9.2/jquery-ui.min.js");
	addStylesheet("http://www.nationstates.net/ghbuttons_v2.css");

	if (isSettingEnabled("forum_enhancements")) {
		if (isSettingEnabled("egosearch_ignore")) {
			addJavascript(urlPrefix + 'forum_ego_posts.js');
		}
		if (isSettingEnabled("post_ids")) {
			console.log("post_ids: " + localStorage.getItem("post_ids"));
			addJavascript(urlPrefix + 'forum_post_id.js');
		}
	}

	console.log('[NationStates++] Loading Completed Successfully.');
}

function isSettingEnabled(setting) {
	return localStorage.getItem(setting) == null || localStorage.getItem(setting) == "true";
}

var _debugMode = false;
function debugConsole(message) {
	if (_debugMode) console.log(message);
}

function addStylesheet(url) {
	var style = document.createElement('link');
	style.setAttribute('rel', 'stylesheet');
	style.setAttribute('type', 'text/css');
	style.setAttribute('href', url);
	document.head.appendChild(style);
}

function addJavascript(url) {
	var script = document.createElement('script');
	script.src = url;
	script.addEventListener('load', function() { });
	document.head.appendChild(script);
}
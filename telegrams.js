function doTelegramSetup() {
	if (typeof _commonsLoaded == 'undefined') {
		setTimeout(doTelegramSetup, 50);
	} else {
		linkifyTelegrams();
	}
}
doTelegramSetup();

function linkifyTelegrams() {
	if (getVisiblePage() == "telegrams" || getVisiblePage() == "tg") {
		$(".tgcontent").children("p").each(function() {
			$(this).html(linkify($(this).html()));
		});
	}
}
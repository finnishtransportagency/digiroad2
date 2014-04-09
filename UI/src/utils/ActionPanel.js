(function(ActionPanel) {

    var panelControl =
        '<div class="panelControl">' +
                        '<div class="panelControlLine"></div>' +
                        '<div class="panelControlLine"></div>' +
                        '<div class="panelControlLine"></div>' +
                    '</div>'+
            '<div class="panelLayerGroup"></div>';


    jQuery("#maptools").append(panelControl);
    AssetActionPanel('asset', 'Joukkoliikenteen pysäkit', 'bus-stop.png');
    AssetActionPanel('linearAsset', 'Nopeusrajoitukset', 'speed-limit.png');
    eventbus.trigger('layer:selected','asset');
}(window.ActionPanel = window.ActionPanel));

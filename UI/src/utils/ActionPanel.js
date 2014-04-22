(function(ActionPanel) {

    var panelControl =
        '<div class="panelControl">' +
                        '<div class="panelControlLine"></div>' +
                        '<div class="panelControlLine"></div>' +
                        '<div class="panelControlLine"></div>' +
                    '</div>'+
            '<div class="panelLayerGroup"></div>';


    jQuery("#maptools").append(panelControl);
    AssetActionPanel('asset', 'Joukkoliikenteen pysäkit', true, 'bus-stop.png');
    AssetActionPanel('linearAsset', 'Nopeusrajoitukset', false, 'speed-limit.png');
    eventbus.trigger('layer:selected','asset');
    Backend.getUserRoles();
}(window.ActionPanel = window.ActionPanel));

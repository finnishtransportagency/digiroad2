(function(ActionPanel) {

    var panelControl =
        '<div class="panelControl">' +
                        '<div class="panelControlLine"></div>' +
                        '<div class="panelControlLine"></div>' +
                        '<div class="panelControlLine"></div>' +
                    '</div>'+
            '<div class="panelLayerGroup"></div>';


    jQuery("#maptools").append(panelControl);
    AssetActionPanel('asset', 'Joukkoliikenteen pysäkit', true, [
        {id: "current", label: "Voimassaolevat", selected: true},
        {id: "future", label: "Tulevat"},
        {id: "past", label: "Käytöstä poistuneet"}
    ], true);
    AssetActionPanel('linearAsset', 'Nopeusrajoitukset', false, [], false);
    eventbus.trigger('layer:selected','asset');
    Backend.getUserRoles();

    var editMessage = $('<div class="editMessage">Olet muokkaustilassa</div>');
    jQuery(".container").append(editMessage.hide());

    var handleEditMessage = function(readOnly) {
        if(readOnly) {
            editMessage.hide();
        } else {
            editMessage.show();
        }
    };

    eventbus.on('application:readOnly', handleEditMessage);

}(window.ActionPanel = window.ActionPanel));

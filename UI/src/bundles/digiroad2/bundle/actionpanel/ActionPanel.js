Oskari.clazz.define("Oskari.digiroad2.bundle.actionpanel.ActionPanel",
    function() {
        this.started = false;
        this.mediator = null;
        this._cursor = {};
        this._readOnly = true;
    }, {
        getName: function() {
            return 'ActionPanel';
        },
        setSandbox: function() {},
        update: function() {
        },
        start: function() {
            if (this.started) {
                return;
            }
            this.started = true;
            // Should this not come as a param?
            var sandbox = Oskari.$('sandbox');
            sandbox.register(this);
            this.setSandbox(sandbox);
            for(var p in this.eventHandlers) {
                if (p) {
                    sandbox.registerForEventByName(this, p);
                }
            }

            var panelControl =
                '<div class="panelControl">' +
                    '<div class="panelControlLine"></div>' +
                    '<div class="panelControlLine"></div>' +
                    '<div class="panelControlLine"></div>' +
                '</div>' +
                '<div class="panelLayerGroup"></div>';


            jQuery("#maptools").append(panelControl);
            new AssetActionPanel('asset', 'Joukkoliikenteen pysäkit');
            new AssetActionPanel('linearAsset', 'Nopeusrajoitukset');
            eventbus.trigger('layer:selected','asset');



        },
        init: function() { }
    }, {
        protocol : ['Oskari.bundle.BundleInstance', 'Oskari.mapframework.module.Module']
    });
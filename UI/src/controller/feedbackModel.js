(function (root) {
    root.FeedbackModel = function(feedbackBackend, assetConfiguration, model) {
        var me = this;
        me.backend = feedbackBackend;
        me.assetConfig = assetConfiguration;
        me.model = model;

        this.sendFeedbackApplication = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            me.backend.sendFeedbackApplication(convertFromToJSON(data), success, failure);
        };


        this.sendFeedbackData = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            me.backend.sendFeedbackData(convertFromToJSON(data), success, failure);
        };


        this.get = function () {
            var assetInfo = getAssetInfo();
            var selected = me.model.get();
            var linkId;
            var assetId;

            //Chain
            if(_.isArray(selected)){
                linkId = _.map(selected, function (selectedAsset) { return selectedAsset.linkId;}).filter(Boolean);
                assetId=  _.map(selected, function (selectedAsset) { return selectedAsset.id; }).filter(Boolean);
            }
            else {
                if(applicationModel.getSelectedLayer() === 'manoeuvre'){
                    assetId = _.map(selected.manoeuvres, function(m) { return m.id; });
                    linkId =  [selected.linkId];
                }
                else {
                    linkId = selected.linkId ? [selected.linkId] : _.map(selected.assets, function (asset) { return asset.linkId; }).filter(Boolean);
                    if (assetInfo.typeId === me.assetConfig.assetTypes.massTransitStop)
                        assetId = [selected.payload.nationalId];
                    else
                        assetId = selected.id ? [selected.id] : _.map(selected.assets, function (asset) { return asset.id; }).filter(Boolean);
                }
            }

            return {
                title: assetInfo.title,
                linkId: linkId,
                assetId: assetId,
                typeId: assetInfo.typeId
            };
        };


        var getAssetInfo = function () {
            var assetTypes = me.assetConfig.assetTypes;
            var assetInfo = me.assetConfig.assetTypeInfo;
            var typeId = assetTypes[applicationModel.getSelectedLayer()];

            return { typeId : typeId,
                     title : typeId ? _.find(assetInfo, function(conf) {return conf.typeId === typeId; }).title : 'Tielinkki'  };

        };

        var convertFromToJSON = function (form) {
            var json = {};
            jQuery.each(form, function () {
                json[this.name] = this.value || '';
            });
            return JSON.stringify({body: json});

        };
    };
})(this);
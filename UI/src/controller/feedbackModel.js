(function (root) {
    root.FeedbackModel = function(feedbackBackend) {
        var me = this;
        var backend = feedbackBackend;


        this.sendFeedbackApplication = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            backend.sendFeedbackApplication(convertFromToJSON(data), success, failure);
        };


        this.sendFeedbackData = function (data) {
            var success = function () {
                eventbus.trigger("feedback:send");
            };

            var failure = function () {
                eventbus.trigger("feedback:failed");
            };
            backend.sendFeedbackData(convertFromToJSON(data), success, failure);
        };


        this.get = function (model) {
            var selected = model.get();
            if(_.isArray(selected))
                return {
                    linkId: _.map(selected, function (selectedAsset) { return selectedAsset.linkId;}).filter(Boolean).join(", "),
                    assetId: _.map(selected, function (selectedAsset) { return selectedAsset.id; }).filter(Boolean).join(", ")
                };
            else
                return {
                    linkId: selected.linkId ? selected.linkId: _.map(selected.assets, function(asset) { return asset.linkId; }).filter(Boolean).join(", "),
                    assetId: selected.id ? selected.id : _.map(selected.assets, function(asset) { return asset.id; }).filter(Boolean).join(", ")
            };
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
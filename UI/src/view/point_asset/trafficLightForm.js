(function(root) {
    root.TrafficLightForm = function() {
        PointAssetForm.call(this);
        var me = this;

        var propertyOrdering = [
            'trafficLight_type',
            'trafficLight_info',
            'trafficLight_municipality_id',
            'trafficLight_structure',
            'trafficLight_height',
            'trafficLight_sound_signal',
            'trafficLight_vehicle_detection',
            'trafficLight_push_button',
            'trafficLight_relative_position',
            'location_coordinates_x',
            'location_coordinates_y',
            'trafficLight_lane',
            'trafficLight_lane_type',
            'trafficLight_state',
            'suggest_box',
            'counter'
        ];

        this.renderValueElement = function(asset, collection, authorizationPolicy) {
            return me.renderComponents(asset, propertyOrdering, authorizationPolicy);
        };

        this.boxEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection){

            rootElement.find('.form-point-asset input[type=text]').on('change input', function (event) {
                var eventTarget = $(event.currentTarget);
                var propertyPublicId = eventTarget.attr('id');
                var propertyValue = eventTarget.val();
                selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
            });

            var singleChoiceIds =
                ['trafficLight_type', 'trafficLight_relative_position', 'trafficLight_structure', 'trafficLight_sound_signal', 'trafficLight_vehicle_detection',
                 'trafficLight_push_button', 'trafficLight_lane_type', 'trafficLight_state'];
            _.forEach(singleChoiceIds, function (publicId) {
                me.bindSingleChoiceElement(rootElement, selectedAsset, publicId);
            });
        };
    };
})(this);
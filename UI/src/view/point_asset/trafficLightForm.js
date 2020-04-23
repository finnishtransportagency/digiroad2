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

        //might be needed if the client wants to see properties of the old traffic light form
       /* var oldAssetPropertyOrdering = [
            'suggest_box',
            'counter'
        ];*/

        var renderOldToNewTrafficLightForm = function (selectedAsset, authorizationPolicy) {
            var wrapper = $('<div class="wrapper">');
            var formRootElement = $('<div class="form form-horizontal form-dark form-point-asset">');
            var oldToNewButton =  $('' +
            '    <div class="form-group point-asset edit-only">' +
            '      <label class="control-label"></label>' +
            '      <button id="old-to-new-traffic-light" class="btn btn-secondary old-to-new-asset">Siirry muokkaamaan liikennevaloa</button>' +
            '    </div>');

            formRootElement = formRootElement
                .append(oldToNewButton);
                //might be needed if the client wants to see properties of the old traffic light form
                //.append(me.renderComponents(selectedAsset, oldAssetPropertyOrdering, authorizationPolicy));

            return wrapper.append(formRootElement);
        };

        this.renderForm = function(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
            var id = selectedAsset.getId();
            var asset = selectedAsset.get();

            var isOldTrafficLight = _.head(me.getProperties(asset.propertyData, 'trafficLight_type').values).propertyValue === "";

            var title;
            if (isOldTrafficLight) {
                title = 'Vanhan tietomallin mukainen liikennevalo';
            } else {
                title = (selectedAsset.isNew() && !selectedAsset.getWasOldAsset()) ? "Uusi " + localizedTexts.newAssetLabel : 'ID: ' + id;
            }
            var header = '<span>' + title + '</span>';
            var form = isOldTrafficLight ? renderOldToNewTrafficLightForm(asset, authorizationPolicy) : me.renderAssetFormElements(selectedAsset, localizedTexts, collection, authorizationPolicy);
            var footer = me.renderButtons();

            rootElement.find("#feature-attributes-header").html(header);
            rootElement.find("#feature-attributes-form").html(form);
            rootElement.find("#feature-attributes-footer").html(footer);

            if (isOldTrafficLight) {
                rootElement.find('button#old-to-new-traffic-light').on('click', function() {
                    var suggestBoxProperty = me.getProperties(asset.propertyData, 'suggest_box');
                    var newAssetProperties = _.cloneDeep(me.pointAsset.newAsset);
                    _.find(newAssetProperties.propertyData, {'publicId': suggestBoxProperty.publicId}).values = suggestBoxProperty.values;
                    selectedAsset.set(newAssetProperties);
                    selectedAsset.setWasOldAsset(true);
                    reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
                });
            } else {
                me.addingPreBoxEventListeners(rootElement, selectedAsset, id);

                rootElement.find('button#change-validity-direction').on('click', function() {
                    var previousValidityDirection = selectedAsset.get().validityDirection;
                    selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
                });

                me.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            }
        };

        var reloadForm = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
            rootElement.find("#feature-attributes-header").empty();
            rootElement.find("#feature-attributes-form").empty();
            rootElement.find("#feature-attributes-footer").empty();
            me.renderForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            me.toggleMode(rootElement, !authorizationPolicy.formEditModeAccess(selectedAsset, roadCollection) || me.applicationModel.isReadOnly());
            rootElement.find('.form-controls button').prop('disabled', !(selectedAsset.isDirty() && me.saveCondition(selectedAsset, authorizationPolicy)));
            rootElement.find('button#cancel-button').prop('disabled', false);
        };

        this.addingPreBoxEventListeners = function (rootElement, selectedAsset, id) {
            rootElement.find('#delete-checkbox').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
            });

            rootElement.find('input[type=checkbox]').not('.suggested-checkbox').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                var propertyPublicId = eventTarget.attr('id');
                var propertyValue = +eventTarget.prop('checked');
                selectedAsset.setPropertyByPublicId(propertyPublicId, propertyValue);
            });

            rootElement.find('.suggested-checkbox').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                selectedAsset.setPropertyByPublicId(eventTarget.attr('id'), +eventTarget.prop('checked'));

                if (id && !selectedAsset.getWasOldAsset()) {
                    me.switchSuggestedValue(true);
                    rootElement.find('.suggested-checkbox').prop('checked', false);
                }
            });

            rootElement.find('.editable').not('.suggestion-box').on('change, click', function () {
                if (id && !selectedAsset.getWasOldAsset()) {
                    me.switchSuggestedValue(true);
                    rootElement.find('.suggested-checkbox').prop('checked', false);
                    selectedAsset.setPropertyByPublicId($('.suggested-checkbox').attr('id'), 0);
                }
            });

            rootElement.find('.point-asset button.save').on('click', function () {
                selectedAsset.save();
            });

            rootElement.find('.point-asset button.cancel').on('click', function () {
                me.switchSuggestedValue(false);
                selectedAsset.cancel();
            });
        };

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
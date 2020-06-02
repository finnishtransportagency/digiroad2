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

        var isOld = function(asset){
            return _.head(me.getProperties(asset.propertyData, 'trafficLight_type').values).propertyValue === "";
        };

        this.renderForm = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
            var id = selectedAsset.getId();
            var asset = selectedAsset.get();

            var isOldTrafficLight = isOld(asset);

            if(!isOldTrafficLight && !selectedAsset.getSelectedGroupedId()) {
                var firstGroupedId = _.head(_.orderBy(asset.propertyData,'groupedId')).groupedId;
                selectedAsset.setSelectedGroupedId(firstGroupedId);
            }

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
            if(me.pointAsset.lanePreview)
                rootElement.find("#feature-attributes-form").prepend(me.renderPreview(roadCollection, selectedAsset));

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
                me.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            }
        };

       this.haveSameSideCode = function(asset) {
            if (!isOld(asset)) {
                var sidecodeProps = _.filter(asset.propertyData, {'publicId': 'sidecode'});
                var sidecodeValue = _.head(_.head(sidecodeProps).values).propertyValue;

                return _.every(sidecodeProps, function(prop){return _.head(prop.values).propertyValue == sidecodeValue;});
            }
            return false;
        };

        this.renderPreview = function(roadCollection, selectedAsset) {
            var asset = selectedAsset.get();
            var lanes;

            if (!asset.floating && this.haveSameSideCode(asset)){
                lanes = roadCollection.getRoadLinkByLinkId(asset.linkId).getData().lanes;
                var sidecodeProps = _.find(asset.propertyData, {'publicId': 'sidecode'});
                var sidecodeValue = _.head(sidecodeProps.values).propertyValue;
                lanes = laneUtils.filterByValidityDirection(sidecodeValue, lanes);
            }

            return _.isEmpty(lanes) ? '' : laneUtils.createPreviewHeaderElement(_.uniq(lanes));
        };

        this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection, authorizationPolicy) {
            var asset = selectedAsset.get();
            var wrapper = $('<div class="wrapper">');
            var formRootElement = $('<div class="form form-horizontal form-dark form-point-asset">');

            if (selectedAsset.isNew() && !selectedAsset.getWasOldAsset()) {
                formRootElement = formRootElement
                    .append(me.renderValueElement(selectedAsset, collection, authorizationPolicy));
            } else {
                var deleteCheckbox = $(''+
                    '    <div class="form-group form-group delete">' +
                    '      <div class="checkbox" >' +
                    '        <input id="delete-checkbox" type="checkbox">' +
                    '      </div>' +
                    '      <p class="form-control-static">Poista</p>' +
                    '    </div>' +
                    '  </div>' );
                var logInfoGroup = $( '' +
                    '    <div class="form-group">' +
                    '      <p class="form-control-static asset-log-info">Lis&auml;tty j&auml;rjestelm&auml;&auml;n: ' + this.informationLog(asset.createdAt, asset.createdBy) + '</p>' +
                    '    </div>' +
                    '    <div class="form-group">' +
                    '      <p class="form-control-static asset-log-info">Muokattu viimeksi: ' + this.informationLog(asset.modifiedAt, asset.modifiedBy) + '</p>' +
                    '    </div>');

                formRootElement = formRootElement
                    .append($(this.renderFloatingNotification(asset.floating, localizedTexts)))
                    .append(logInfoGroup)
                    .append( $(this.userInformationLog(authorizationPolicy, selectedAsset)))
                    .append(me.renderValueElement(selectedAsset, collection, authorizationPolicy))
                    .append(deleteCheckbox);
            }
            return wrapper.append(formRootElement);
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

            rootElement.find('.suggested-checkbox').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                var propertyGroupedId = eventTarget.data('grouped-id');
                var propertyPublicId = _.head(_.split(eventTarget.attr('id'), '-'));
                selectedAsset.setPropertyByGroupedIdAndPublicId(propertyGroupedId, propertyPublicId, +eventTarget.prop('checked'));

                if (id && !selectedAsset.getWasOldAsset()) {
                    me.switchSuggestedValue(true);
                    rootElement.find('.suggested-checkbox').prop('checked', false);
                }
            });

            rootElement.find('.editable').not('.suggestion-box').on('change', function () {
                if (id && !selectedAsset.getWasOldAsset()) {
                    me.switchSuggestedValue(true);
                    rootElement.find('.suggested-checkbox').prop('checked', false);
                    _.forEach(rootElement.find('.suggested-checkbox'), function (element) {
                        var targetElement = $(element);
                        var propertyGroupedId = targetElement.data('grouped-id');
                        var propertyPublicId = _.head(_.split(targetElement.attr('id'), '-'));
                        selectedAsset.setPropertyByGroupedIdAndPublicId(propertyGroupedId, propertyPublicId, 0);
                    });
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

        this.selectableAssetButton = function(selectedAsset, assetNumber, groupedId) {
            var assetButton = $('<span groupedId="' + groupedId + '" class="selectable">' + assetNumber + '</span>');
            if (selectedAsset.getSelectedGroupedId() == groupedId){
                assetButton.addClass('marker-highlight');
            } else {
                assetButton.addClass('marker');
            }

            return assetButton;
        };

        this.renderValidityDirection = function (id) {
            return '' +
              '    <div class="form-group editable form-directional-traffic-sign edit-only">' +
              '      <label class="control-label">Vaikutussuunta</label>' +
              '      <button groupedId="'+id+'" class="change-validity-direction form-control btn btn-secondary btn-block">Vaihda suuntaa</button>' +
              '    </div>';
        };

        this.renderValueElement = function(selectedAsset, collection, authorizationPolicy) {
            var toRet = $();
            var i = 0;
            var allProperties = _.groupBy(selectedAsset.get().propertyData, 'groupedId');
            _.forEach(allProperties, function (currentProperties) {
                var currentGroupId = _.head(currentProperties).groupedId;
                var trafficLightContainer = $('<div class="traffic-light-container" id="traffic-light-container-'+ (++i) +'">');
                toRet = toRet.add(
                    trafficLightContainer
                        .append(me.selectableAssetButton(selectedAsset, i, currentGroupId))
                        .append(me.renderComponents(currentProperties, propertyOrdering, authorizationPolicy))
                        .append(me.attachControlButtons(i))
                        .append($('<hr>'))
                );
                trafficLightContainer.find(".suggestion-box").before($(me.renderValidityDirection(currentGroupId)));
            });
            return toRet;
        };

        this.attachControlButtons = function (index) {
           return '' +
                '    <div class="form-group editable edit-only">' +
                '       <button class="btn edit-only btn-secondary button-remove-traffic-light" id="button-remove-traffic-light-'+index+'" data-index="'+index+'" >Poista opastinlaite</button>' +
                '       <button class="btn edit-only editable btn-secondary button-add-traffic-light" id="button-add-traffic-light-'+index+'" data-index="'+index+'" >Lisää opastinlaite</button>' +
                '    </div>';
        };

        this.boxEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection){

            rootElement.find('.form-point-asset input[type=text],select').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                var propertyGroupedId = eventTarget.data('grouped-id');
                var propertyPublicId = _.head(_.split(eventTarget.attr('id'), '-'));
                selectedAsset.setPropertyByGroupedIdAndPublicId(propertyGroupedId, propertyPublicId,  eventTarget.val());
            });

            me.bindExtendedFormEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
        };

        this.bindExtendedFormEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
            rootElement.find('.marker').on('click', function (event) {
                var groupId = $(event.currentTarget).attr('groupedId');
                selectedAsset.setSelectedGroupedId(groupId);
                reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            });

            rootElement.find('button.change-validity-direction').on('click', function (event) {
                var sidecodePublicId = 'sidecode';
                var groupId = $(event.currentTarget).attr('groupedId');
                var previousSidecode = _.head(selectedAsset.getPropertyByGroupedIdAndPublicId(groupId, sidecodePublicId).values).propertyValue;
                selectedAsset.setPropertyByGroupedIdAndPublicId(groupId, sidecodePublicId, validitydirections.switchDirection(previousSidecode));
                reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            });

            rootElement.find('.button-remove-traffic-light').on('click', function (event) {
                var index = $(event.currentTarget).data('index');
                var containerProperties = $('#traffic-light-container-' + index).children();
                var groupedId = getDatasetFromChild(containerProperties[1]);
                if (groupedId) {
                    selectedAsset.removePropertyByGroupedId(groupedId);
                    var firstGroupId = _.head(selectedAsset.get().propertyData).groupedId;
                    selectedAsset.setSelectedGroupedId(firstGroupId);
                }
                reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            });

            rootElement.find('.button-add-traffic-light').on('click', function (event) {
                var form = $('.form-point-asset').children();
                var lastChild = form.get(form.length - 1);
                var lastKnownGroupedId = $(lastChild).data('grouped-id');
                addPropertiesForNewTrafficLight(selectedAsset, lastKnownGroupedId);
                reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            });

           toggleButtonVisibility(rootElement);
        };

        var addPropertiesForNewTrafficLight = function (selectedAsset, lastKnownGroupedId) {
            var newProperties = _.cloneDeep(me.pointAsset.newAsset.propertyData);
            var newId = ++lastKnownGroupedId;
            _.forEach(newProperties, function (property) {
               property.groupedId = newId;
            });
            selectedAsset.addAdditionalTrafficLight(newProperties);
        };

        var getDatasetFromChild = function(property) {
            var elementWithDataset = _.find(property.children, function (child) {
                return child.getAttribute('data-grouped-id') !== null;
            });
            return elementWithDataset ? elementWithDataset.getAttribute('data-grouped-id') : elementWithDataset;
        };

        var toggleButtonVisibility = function (rootElement) {
            var containers = rootElement.find('.traffic-light-container');
            var amount = containers.length;

            containers.find('.button-remove-traffic-light').prop("disabled", amount === 1);
            containers.find('.button-add-traffic-light').prop("disabled", amount >= 6);
        };

        this.suggestedBoxHandler = function(asset, authorizationPolicy) {
            var suggestedBoxValue = _.isEmpty(asset.values) ? 0 : !!parseInt(_.head(asset.values).propertyValue);
            var suggestedBoxDisabledState = me.getSuggestedBoxDisabledState();

            if(suggestedBoxDisabledState) {
                return me.renderSuggestBoxElement(asset, 'disabled');
            } else if(me.pointAsset.isSuggestedAsset && authorizationPolicy.handleSuggestedAsset(me.selectedAsset, suggestedBoxValue)) {
                var checkedValue = suggestedBoxValue ? 'checked' : '';
                return me.renderSuggestBoxElement(asset, checkedValue);
            } else {
                // empty div placed for correct positioning on the form for some elements to appear before or after the suggestion-box
                return '<div class="form-group editable form-' + me.pointAsset.layerName + ' suggestion-box"></div>';
            }
        };
    };
})(this);
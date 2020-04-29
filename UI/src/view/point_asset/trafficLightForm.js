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

        this.renderForm = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
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

                rootElement.find('button#change-validity-direction').on('click', function() {
                    var previousValidityDirection = selectedAsset.get().validityDirection;
                    selectedAsset.set({ validityDirection: validitydirections.switchDirection(previousValidityDirection) });
                    if(me.pointAsset.lanePreview)
                        $('.preview-div').replaceWith(me.renderPreview(roadCollection, selectedAsset));
                });

                me.boxEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            }
        };

        this.renderAssetFormElements = function(selectedAsset, localizedTexts, collection, authorizationPolicy) {
            var asset = selectedAsset.get();
            var wrapper = $('<div class="wrapper">');
            var formRootElement = $('<div class="form form-horizontal form-dark form-point-asset">');

            if (selectedAsset.isNew() && !selectedAsset.getWasOldAsset()) {
                formRootElement = formRootElement
                    .append(me.renderValueElement(asset, collection, authorizationPolicy))
                    .append(me.renderValidityDirection(selectedAsset));
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
                    .append(me.renderValueElement(asset, collection, authorizationPolicy))
                    .append(me.renderValidityDirection(selectedAsset))
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

        //TODO: this needs to be tested with new form and multiple lights on an update of existing
        this.addingPreBoxEventListeners = function (rootElement, selectedAsset, id) {
            rootElement.find('#delete-checkbox').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                selectedAsset.set({toBeDeleted: eventTarget.prop('checked')});
            });

            rootElement.find('.suggested-checkbox').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                selectedAsset.setPropertyByCreationId(eventTarget.data('creationId'), +eventTarget.prop('checked'));

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
                        selectedAsset.setPropertyByCreationId(element.data('creationId'), 0);
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

        this.renderValueElement = function(asset, collection, authorizationPolicy) {
            var toRet = $();
            var allProperties = _.orderBy(asset.propertyData, ['index'], ['asc']);
            for (var i = 1; allProperties.length/(propertyOrdering.length - 1) >= i ; i++) {
                var currentProperties = allProperties.slice((i - 1) * (propertyOrdering.length - 1) , i * (propertyOrdering.length - 1));
                var trafficLightContainer = $('<div class="traffic-light-container" id="traffic-light-container-'+i+'">');
                toRet = toRet.add(
                    trafficLightContainer
                        .append(me.renderComponents(currentProperties, propertyOrdering, authorizationPolicy))
                        .append(me.attachControlButtons(i))
                        .append($('<hr>'))
                );
            }
            return toRet;
        };

        this.attachControlButtons = function (id) {
           return '' +
                '    <div class="form-group editable edit-only">' +
                '       <button class="btn edit-only btn-secondary button-remove-traffic-light" id="button-remove-traffic-light-'+id+'" data-index="'+id+'" >Poista opastinlaite</button>' +
                '       <button class="btn edit-only editable btn-secondary button-add-traffic-light" id="button-add-traffic-light-'+id+'" data-index="'+id+'" >Lisää opastinlaite</button>' +
                '    </div>';
        };

        this.boxEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection){

            rootElement.find('.form-point-asset input[type=text],select').on('change', function (event) {
                var eventTarget = $(event.currentTarget);
                var propertyCreationId = eventTarget.data('creation-id');
                selectedAsset.setPropertyByCreationId(propertyCreationId,  eventTarget.val());
            });

            me.bindExtendedFormEvents(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
        };

        this.bindExtendedFormEvents = function (rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection) {
            rootElement.find('.button-remove-traffic-light').on('click', function (event) {
                var index = $(event.currentTarget).data('index');
                var containerProperties = $('#traffic-light-container-' + index).children();
                _.forEach(containerProperties, function (property) {
                    var creationId = getDatasetFromChild(property);
                    if(creationId)
                        selectedAsset.removePropertyByCreationId(creationId);
                });
                reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            });

            rootElement.find('.button-add-traffic-light').on('click', function (event) {
                var form = $('.form-point-asset').children();
                var lastChild = form.get(form.length - 1);
                var lastKnownCreationId = $(lastChild).data('creation-id');
                addPropertiesForNewTrafficLight(selectedAsset, lastKnownCreationId);
                reloadForm(rootElement, selectedAsset, localizedTexts, authorizationPolicy, roadCollection, collection);
            });

           toggleButtonVisibility(rootElement);
        };

        var addPropertiesForNewTrafficLight = function (selectedAsset, lastKnownCreationId) {
            var newProperties = _.cloneDeep(me.pointAsset.newAsset.propertyData);
            var newId = lastKnownCreationId;
            _.forEach(newProperties, function (property) {
               property.creationId = ++newId;
            });
            selectedAsset.addAdditionalTrafficLight(newProperties);
        };

        var getDatasetFromChild = function(property) {
            var elementWithDataset = _.find(property.children, function (child) {
                return child.getAttribute('data-creation-id') !== null;
            });
            return elementWithDataset ? elementWithDataset.getAttribute('data-creation-id') : elementWithDataset;
        };

        var toggleButtonVisibility = function (rootElement) {
            var containers = rootElement.find('.traffic-light-container');
            var amount = containers.length;

            containers.find('.button-remove-traffic-light').prop("disabled", amount === 1);
            containers.find('.button-add-traffic-light').prop("disabled", amount === 6);
        };
    };
})(this);
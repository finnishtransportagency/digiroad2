(function (root) {
    root.FeedbackDataTool = function () {
        var me = this;
        me.collection= null;
        me.layerName = '';
        me.authorizationPolicy = null;
        me.eventCategory = null;

        this.initialize = function(feedbackCollection, layerName, authorizationPolicy, eventCategory){
            me.collection = feedbackCollection;
            me.layerName = layerName;
            me.authorizationPolicy = authorizationPolicy;
            me.eventCategory = eventCategory;
            applicationListeners();
        };

        function events() {
            return _.map(arguments, function(argument) { return me.eventCategory + ':' + argument; }).join(' ');
        }

        var renderFeedbackLink = function (enable) {
            var infoContent = $('#information-content');
            if (enable && !infoContent.find('#feedback-data').length)
                infoContent.append('<a id="feedback-data" href="javascript:void(0)" class="feedback-data-link" >Anna palautetta kohteesta</a>');
            else if(!enable) {
                infoContent.find('#feedback-data').remove();
            }

            $('#feedback-data').on('click', function(){
               open();
            });
        };

        var open = function(){
            if(applicationModel.getSelectedLayer() === me.layerName) {
                var selectedData = getData();
                renderDialog(selectedData, me.layerName);
                bindEvents(selectedData);
                applicationModel.setApplicationkState(applicationState.Feedback);
            }
        };

        this.closeFeedback = function(){
            purge();
            renderFeedbackLink(false);
            applicationModel.setApplicationkState(applicationState.Normal);
        };

        this.initFeedback = function(){
            renderFeedbackLink(true);
        };

        var applicationListeners = function(){
            eventbus.on("feedback:send", function() {
                removeSpinner();
                new GenericConfirmPopup("Kiitos palautteesta", {type: 'alert'});
                me.closeFeedback();
            });
            eventbus.on("feedback:failed",function() {
                removeSpinner();
                new GenericConfirmPopup("Palautteen lähetyksessä esiintyi virhe. Yritys toistuu automaattisesti hetken päästä.", {type: 'alert'});
            });

            eventbus.on('linkProperties:unselected', me.closeFeedback);

            eventbus.on('linkProperties:selected linkProperties:cancelled', me.initFeedback);

            eventbus.on(events('selected', 'cancelled'), me.initFeedback);

            eventbus.on(events('unselect'), me.closeFeedback);

            eventbus.on('manoeuvres:selected manoeuvres:cancelled',me.initFeedback);

            eventbus.on('manoeuvres:unselected', me.closeFeedback);

            eventbus.on('speedLimit:unselect', me.closeFeedback);

            eventbus.on('speedLimit:selected speedLimit:cancelled',me.initFeedback);

            eventbus.on(me.layerName + ':unselected', me.closeFeedback);

            eventbus.on(me.layerName + ':selected ' + me.layerName + ':cancelled' ,me.initFeedback);

            eventbus.on('asset:modified', me.initFeedback);

            eventbus.on('asset:closed', me.closeFeedback);
        };

        var bindEvents = function (selectedData) {
            $('.feedback-modal .cancel').on('click', function() {
                $('.feedback-modal :input').val('');
                setDropdownValue( me.layerName,  $('.feedback-modal'));
            });

            $('.feedback-modal .save').on('click', function() {
                addSpinner();
                applicationModel.setApplicationkState('normal');
                var values = $(".form-horizontal").serializeArray();
                values.push(
                        {name: 'linkId',    value: selectedData.linkId},
                        {name: 'assetId',   value : selectedData.assetId },
                        {name: 'assetName', value : selectedData.title});
                me.collection.sendFeedbackData(values);
            });

            $(' .feedback-modal .sulje').on('click', function() {
                applicationModel.setApplicationkState('normal');
                purge();
            });

        };

        var addSpinner = function () {
            $('.container').append('<div class="spinner-overlay modal-overlay"><div class="spinner"></div></div>');
        };

        var removeSpinner = function(){
            $('.spinner-overlay').remove();
        };

        var purge = function() {
            $('.feedback-modal').remove();
        };


        var renderDialog = function(selectedAsset, layer) {
            var dialog = createFeedbackForm(selectedAsset, layer);
            $('.container').append(dialog);
        };

        var getData = function(){
            return me.collection.get();
        };

        var setDropdownValue = function(layer, dialog){
           if(layer === 'linkProperty')
               dialog.find('#feedbackDataType').val('Geometriapalaute');
           else
               dialog.find('#feedbackDataType').val('Aineistopalaute');
        };

        var userEditableFields = function(){
            return $(
                     '<label class="control-label">Nimi</label>' +
                     '<input type="text" name="name" class="form-control">' +

                     '<label class="control-label">Sähköposti</label>' +
                     '<input type="text" name="email" class="form-control">' +

                     '<label class="control-label">Puhelinnumero</label>' +
                     '<input type="text" name="phoneNumber" id="phoneNumber" class="form-control">');

        };

        var createFeedbackForm = function(selectedAsset, layer) {


           var  dialog =  $('<div class="feedback-modal" id="feedbackData">' +
                        '<div class="modal-dialog">' +
                            '<div class="content">Anna palautetta kohteesta<a class="header-link sulje"">X</a>' + '</div>' +
                            '<form class="form form-horizontal" role="form"">' +
                            '<div class="form-group" id="feedbackForm">' +

                                '<label class="control-label">Linkin id</label>' +
                                '<label id="linkId" ></label>'+

                                '<label class="control-label">Kohteen id</label>' +
                                '<label id="assetId" ></label>'+

                                '<label class="control-label">Tietolaji</label>' +
                                '<label id="assetName"></label>'+

                                '<label class="control-label" id="feedbackType">Palautteen tyyppi</label>' +
                                '<select name="feedbackDataType" id ="feedbackDataType" class="form-control">'+
                                    '<option value="Geometriapalaute">Geometriapalaute </option>'+
                                    '<option value="Aineistopalaute">Aineistopalaute</option>'+
                                '</select>' +

                                '<label class="control-label">Palaute</label>' +
                                '<textarea name="freeText" id="freetext" class="form-control"></textarea>'+

                                '<label class="control-label">K-tunnus</label>' +
                                '<label id="kidentifier"></label>'+
                            '</div>' +
                            '</form>' +
                            '<div class="actions">' +
                                '<button class = "btn btn-primary save">Lähetä</button>' +
                                '<button class = "btn btn-secondary cancel">Peruuta</button>' +
                            '</div>' +
                        '</div>' +
                    '</div>');

           setDropdownValue(layer, dialog);
           dialog.find("#kidentifier").append(me.authorizationPolicy.username);
           dialog.find("#linkId").append(selectedAsset.linkId);
           dialog.find("#assetId").append(selectedAsset.assetId);
           dialog.find("#assetName").append(selectedAsset.title);
           dialog.find("#feedbackForm").append(userEditableFields());
           return dialog;
        };
    };
})(this);
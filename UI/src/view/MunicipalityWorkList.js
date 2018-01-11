(function (root) {
    var hrefDir = "#work-list/municipality/";

    var municipalityTable = function (municipalities, filter) {
        var municipalityValues =
            _.isEmpty(filter) ? municipalities : _.filter(municipalities, function (municipality) {
                return municipality.name.toLowerCase().startsWith(filter.toLowerCase());});

        var tableContentRows = function (municipalities) {
            return _.map(municipalities, function (municipality) {
                return $('<tr/>').append($('<td/>').append(idLink(municipality)));
            });
        };
        var idLink = function (municipality) {
            return $('<a class="work-list-item"/>').attr('href', hrefDir + municipality.id).html(municipality.name);
        };

        return $('<table id="tableData"/>').append(tableContentRows(municipalityValues));
    };

    var searchbox = $('<div class="filter-box">' +
        '<input type="text" class="location input-sm" placeholder="Kuntanimi" id="searchBox"></div>');

    var generateWorkList = function (listP) {
        var title = 'Tietolajien kuntasivu';
        $('#work-list').html('' +
            '<div style="overflow: auto;">' +
            '<div class="page">' +
            '<div class="content-box">' +
            '<header>' + title +
            '<a class="header-link" href="#" onclick="windows.location">Sulje</a>' +
            '</header>' +
            '<div class="work-list">' +
            '</div>' +
            '</div>' +
            '</div>'
        );
        var showApp = function () {
            $('.container').show();
            $('#work-list').hide();
            $('body').removeClass('scrollable').scrollTop(0);
        };

        listP.then(function (limits) {
            var element = $('#work-list .work-list');
            if (limits.length == 1)
                window.location = hrefDir + _.map(limits, function(limit) {return limit.id;});

            var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, ""]))();
            element.html($('<div class="municipality-list">').append(unknownLimits));

             if (_.contains(userRoles, 'operator') || _.contains(userRoles, 'premium'))
                 searchbox.insertBefore('table');

            $('#searchBox').on('keyup', function(event){
                var currentInput = event.currentTarget.value;

                var unknownLimits = _.partial.apply(null, [municipalityTable].concat([limits, currentInput]))();
                $('#tableData tbody').html(unknownLimits);
            });
        });
    };

    var bindExternalEventHandlers = function() {
        eventbus.on('roles:fetched', function(roles) {
            userRoles = roles;
        });
    };

    var bindEvents = function () {
        eventbus.on('municipality:select', function(listP) {
            $('.container').hide();
            $('#work-list').show();
            $('#municipality-work-list').hide();
            $('body').addClass('scrollable');
            generateWorkList(listP);
        });
    };

    root.MunicipalityWorkList =  {
        initialize: function () {
            var userRoles;
            bindExternalEventHandlers();
            bindEvents();
        }
    };
})(this);
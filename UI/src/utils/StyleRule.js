(function(root) {
    root.StyleRule = function(){
        var expressionFn = [];
        var style;

        var runExpression = function(expression, obj, previousCondition){
            if(previousCondition)
                return expression.compareExpressions(previousCondition, expression.compare(expression.getValue(obj)));
            return expression.compare(expression.getValue(obj));
        };

        var generateExpression = function(property, propertyValue){
            var expression = propertyValue ?
                {getValue: function(){return propertyValue;}} :
                {getValue: function(obj){return obj[property];}};
            expressionFn.push(expression);
            return expression;
        };

        this.where = function(property, propertyValue){
            generateExpression(property, propertyValue);
            return this;
        };

        this.and = function(property, propertyValue){
            if(expressionFn.length === 0)
                throw 'You must have a "where" function before use the "and" function.';
            var expression = generateExpression(property, propertyValue);
            expression.compareExpressions = function(arg1, arg2) { return arg1 && arg2; };
            return this;
        };

        this.or = function(property, propertyValue){
            if(expressionFn.length === 0)
                throw 'You must have a "where" function before use the "or" function.';
            var expression = generateExpression(property, propertyValue);
            expression.compareExpressions = function(arg1, arg2) { return arg1 || arg2; };
            return this;
        };

        this.is = function(value){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "is".';
            expression.compare = function(propertyValue){
                return propertyValue == value;
            };
            return this;
        };

        this.isNot = function(value){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "isNot".';
            expression.compare = function(propertyValue){
                return propertyValue != value;
            };
            return this;
        };

        this.isIn = function(values){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "isIn".';
            expression.compare = function(propertyValue){
                for(var i=0; i < values.length ; ++i)
                    if(values[i] == propertyValue)
                        return true;
                return false;
            };
            return this;
        };

        this.isNotIn = function(values){
            var expression = expressionFn[expressionFn.length-1];
            if(!expression || expression.compare)
                throw 'You must have on of the following functions ["where", "and", "or"] before use the "isNotIn".';
            expression.compare = function(propertyValue){
                var exists = false;
                for(var i=0; i < values.length ; ++i)
                    if(values[i] == propertyValue)
                        exists = true;
                return !exists;
            };
            return this;
        };

        this.use = function(obj){
            style = obj;
            return this;
        };

        this._match = function(obj){
            if(expressionFn.length === 0)
                return false;

            var condition = runExpression(expressionFn[0], obj);
            for(var i=1 ; i < expressionFn.length; i++)
                condition = runExpression(expressionFn[i], obj, condition);

            return condition;
        };

        this._get = function(){
            return style;
        };
    };

    root.StyleRuleProvider = function(defaultStyle){
        var stateRules = [];
        var defaultRuleName = 'default';
        var openLayerStyleClassConfigs = [
            { name: 'stroke', type: ol.style.Stroke },
            { name: 'fill', type: ol.style.Fill },
            { name: 'image', type: ol.style.Image },
            { name: 'text', type: ol.style.Text }
        ];

        var getOpenLayerStyleClass = function(name){
            for(var i in openLayerStyleClassConfigs)
                if(openLayerStyleClassConfigs[i].name == name)
                    return openLayerStyleClassConfigs[i].type;
            return undefined;
        };

        var createOpenLayerStyle = function(configObj){
            var styleOptions = {};
            for(var propertyName in configObj){
                var olType = getOpenLayerStyleClass(propertyName);
                if(olType)
                    styleOptions[propertyName] = new olType(configObj[propertyName]);
                else
                    styleOptions[propertyName] = configObj[propertyName];
            }
            return new ol.style.Style(styleOptions);
        };

        var getRulesByName = function(name){
            var stateRule  = stateRules[name];
            if(stateRule)
                return stateRule;
            return [];
        };

        var setRulesByName = function(name, rules){
            stateRules[defaultRuleName] = rules;
        };

        this.addRules = function(rules){
            this.addRulesByName(defaultRuleName, rules);
        };

        this.addRulesByName = function(name, rules){
            var allRules = getRulesByName(name);
            for(var i=0; i < rules.length; i++)
                allRules.push(rules[i]);
            setRulesByName(name, allRules);
        };

        this.getStyle = function(context){
           return this.getStyleByName(defaultRuleName, context);
        };

        this.getStyleByName = function(name, context){
            var allRules = getRulesByName(name);
            var configObj = _.merge({}, defaultStyle);
            for(var i=0; i < allRules.length; i++){
                var rule = allRules[i];
                if(rule._match(context)){
                    configObj = _.merge(configObj, rule._get());
                }
            }
            return createOpenLayerStyle(configObj);
        };
    };
})(this);

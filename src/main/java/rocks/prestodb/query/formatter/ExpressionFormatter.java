/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rocks.prestodb.query.formatter;

import com.facebook.presto.sql.tree.AllColumns;
import com.facebook.presto.sql.tree.ArithmeticBinaryExpression;
import com.facebook.presto.sql.tree.ArithmeticUnaryExpression;
import com.facebook.presto.sql.tree.ArrayConstructor;
import com.facebook.presto.sql.tree.AtTimeZone;
import com.facebook.presto.sql.tree.BetweenPredicate;
import com.facebook.presto.sql.tree.BinaryLiteral;
import com.facebook.presto.sql.tree.BooleanLiteral;
import com.facebook.presto.sql.tree.Cast;
import com.facebook.presto.sql.tree.CharLiteral;
import com.facebook.presto.sql.tree.CoalesceExpression;
import com.facebook.presto.sql.tree.ComparisonExpression;
import com.facebook.presto.sql.tree.Cube;
import com.facebook.presto.sql.tree.CurrentTime;
import com.facebook.presto.sql.tree.DecimalLiteral;
import com.facebook.presto.sql.tree.DereferenceExpression;
import com.facebook.presto.sql.tree.DoubleLiteral;
import com.facebook.presto.sql.tree.ExistsPredicate;
import com.facebook.presto.sql.tree.Expression;
import com.facebook.presto.sql.tree.Extract;
import com.facebook.presto.sql.tree.FieldReference;
import com.facebook.presto.sql.tree.FrameBound;
import com.facebook.presto.sql.tree.FunctionCall;
import com.facebook.presto.sql.tree.GenericLiteral;
import com.facebook.presto.sql.tree.GroupingElement;
import com.facebook.presto.sql.tree.GroupingSets;
import com.facebook.presto.sql.tree.Identifier;
import com.facebook.presto.sql.tree.IfExpression;
import com.facebook.presto.sql.tree.InListExpression;
import com.facebook.presto.sql.tree.InPredicate;
import com.facebook.presto.sql.tree.IntervalLiteral;
import com.facebook.presto.sql.tree.IsNotNullPredicate;
import com.facebook.presto.sql.tree.IsNullPredicate;
import com.facebook.presto.sql.tree.LambdaArgumentDeclaration;
import com.facebook.presto.sql.tree.LambdaExpression;
import com.facebook.presto.sql.tree.LikePredicate;
import com.facebook.presto.sql.tree.LogicalBinaryExpression;
import com.facebook.presto.sql.tree.LongLiteral;
import com.facebook.presto.sql.tree.Node;
import com.facebook.presto.sql.tree.NotExpression;
import com.facebook.presto.sql.tree.NullIfExpression;
import com.facebook.presto.sql.tree.NullLiteral;
import com.facebook.presto.sql.tree.Parameter;
import com.facebook.presto.sql.tree.QualifiedName;
import com.facebook.presto.sql.tree.QuantifiedComparisonExpression;
import com.facebook.presto.sql.tree.Rollup;
import com.facebook.presto.sql.tree.Row;
import com.facebook.presto.sql.tree.SearchedCaseExpression;
import com.facebook.presto.sql.tree.SimpleCaseExpression;
import com.facebook.presto.sql.tree.SimpleGroupBy;
import com.facebook.presto.sql.tree.SortItem;
import com.facebook.presto.sql.tree.StackableAstVisitorRocks;
import com.facebook.presto.sql.tree.StackableAstVisitorRocks.StackableAstVisitorContext;
import com.facebook.presto.sql.tree.StringLiteral;
import com.facebook.presto.sql.tree.SubqueryExpression;
import com.facebook.presto.sql.tree.SubscriptExpression;
import com.facebook.presto.sql.tree.SymbolReference;
import com.facebook.presto.sql.tree.TimeLiteral;
import com.facebook.presto.sql.tree.TimestampLiteral;
import com.facebook.presto.sql.tree.TryExpression;
import com.facebook.presto.sql.tree.WhenClause;
import com.facebook.presto.sql.tree.Window;
import com.facebook.presto.sql.tree.WindowFrame;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static rocks.prestodb.query.formatter.StatementFormatter.formatSql;
import static rocks.prestodb.query.formatter.StatementFormatter.indentString;

public final class ExpressionFormatter
{
    private ExpressionFormatter()
    {
    }

    public static String formatExpression(Expression expression, Optional<List<Expression>> parameters, int indent)
    {
        return new Formatter(parameters).process(expression, new StackableAstVisitorContext<>(indent));
    }

    public static class Formatter
            extends StackableAstVisitorRocks<String, Integer>
    {
        private final Optional<List<Expression>> parameters;

        public Formatter(Optional<List<Expression>> parameters)
        {
            this.parameters = parameters;
        }

        @Override
        protected String visitNode(Node node, StackableAstVisitorContext<Integer> indent)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected String visitRow(Row node, StackableAstVisitorContext<Integer> indent)
        {
            return "ROW (" + Joiner.on(", ").join(node.getItems().stream()
                    .map((child) -> process(child, indent))
                    .collect(toList())) + ")";
        }

        @Override
        protected String visitExpression(Expression node, StackableAstVisitorContext<Integer> indent)
        {
            throw new UnsupportedOperationException(format("not yet implemented: %s.visit%s", getClass().getName(), node.getClass().getSimpleName()));
        }

        @Override
        protected String visitAtTimeZone(AtTimeZone node, StackableAstVisitorContext<Integer> indent)
        {
            return new StringBuilder()
                    .append(process(node.getValue(), indent))
                    .append(" AT TIME ZONE ")
                    .append(process(node.getTimeZone(), indent)).toString();
        }

        @Override
        protected String visitCurrentTime(CurrentTime node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder();

            builder.append(node.getType().getName());

            if (node.getPrecision() != null) {
                builder.append('(')
                        .append(node.getPrecision())
                        .append(')');
            }

            return builder.toString();
        }

        @Override
        protected String visitExtract(Extract node, StackableAstVisitorContext<Integer> indent)
        {
            return "EXTRACT(" + node.getField() + " FROM " + process(node.getExpression(), indent) + ")";
        }

        @Override
        protected String visitBooleanLiteral(BooleanLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return String.valueOf(node.getValue());
        }

        @Override
        protected String visitStringLiteral(StringLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return formatStringLiteral(node.getValue());
        }

        @Override
        protected String visitCharLiteral(CharLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return "CHAR " + formatStringLiteral(node.getValue());
        }

        @Override
        protected String visitBinaryLiteral(BinaryLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return "X'" + node.toHexString() + "'";
        }

        @Override
        protected String visitParameter(Parameter node, StackableAstVisitorContext<Integer> indent)
        {
            if (parameters.isPresent()) {
                checkArgument(node.getPosition() < parameters.get().size(), "Invalid parameter number %s.  Max value is %s", node.getPosition(), parameters
                        .get().size() - 1);
                return process(parameters.get().get(node.getPosition()), indent);
            }
            return "?";
        }

        @Override
        protected String visitArrayConstructor(ArrayConstructor node, StackableAstVisitorContext<Integer> indent)
        {
            ImmutableList.Builder<String> valueStrings = ImmutableList.builder();
            for (Expression value : node.getValues()) {
                valueStrings.add(formatExpression(value, parameters, indent.getContext() + 1));
            }
            return "ARRAY[" + Joiner.on(",").join(valueStrings.build()) + "]";
        }

        @Override
        protected String visitSubscriptExpression(SubscriptExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return formatExpression(node.getBase(), parameters, indent.getContext()) + "[" + formatExpression(node.getIndex(), parameters, indent.getContext
                    ()) + "]";
        }

        @Override
        protected String visitLongLiteral(LongLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return Long.toString(node.getValue());
        }

        @Override
        protected String visitDoubleLiteral(DoubleLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return Double.toString(node.getValue());
        }

        @Override
        protected String visitDecimalLiteral(DecimalLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return "DECIMAL '" + node.getValue() + "'";
        }

        @Override
        protected String visitGenericLiteral(GenericLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return node.getType() + " " + formatStringLiteral(node.getValue());
        }

        @Override
        protected String visitTimeLiteral(TimeLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return "TIME '" + node.getValue() + "'";
        }

        @Override
        protected String visitTimestampLiteral(TimestampLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return "TIMESTAMP '" + node.getValue() + "'";
        }

        @Override
        protected String visitNullLiteral(NullLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            return "null";
        }

        @Override
        protected String visitIntervalLiteral(IntervalLiteral node, StackableAstVisitorContext<Integer> indent)
        {
            String sign = (node.getSign() == IntervalLiteral.Sign.NEGATIVE) ? "- " : "";
            StringBuilder builder = new StringBuilder()
                    .append("INTERVAL ")
                    .append(sign)
                    .append(" '").append(node.getValue()).append("' ")
                    .append(node.getStartField());

            if (node.getEndField().isPresent()) {
                builder.append(" TO ").append(node.getEndField().get());
            }
            return builder.toString();
        }

        @Override
        protected String visitSubqueryExpression(SubqueryExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return "(\n" + formatSql(node.getQuery(), parameters, indent.getContext() + 1) + indentString(indent.getContext()) + ')';
        }

        @Override
        protected String visitExists(ExistsPredicate node, StackableAstVisitorContext<Integer> indent)
        {
            return "EXISTS " + process(node.getSubquery(), indent) + indentString(indent.getContext());
        }

        @Override
        protected String visitIdentifier(Identifier node, StackableAstVisitorContext<Integer> indent)
        {
            return formatIdentifier(node.getName());
        }

        @Override
        protected String visitLambdaArgumentDeclaration(LambdaArgumentDeclaration node, StackableAstVisitorContext<Integer> indent)
        {
            return formatIdentifier(node.getName());
        }

        protected String visitSymbolReference(SymbolReference node, StackableAstVisitorContext<Integer> indent)
        {
            return formatIdentifier(node.getName());
        }

        @Override
        protected String visitDereferenceExpression(DereferenceExpression node, StackableAstVisitorContext<Integer> indent)
        {
            String baseString = process(node.getBase(), indent);
            return baseString + "." + formatIdentifier(node.getFieldName());
        }

        private static String formatQualifiedName(QualifiedName name)
        {
            List<String> parts = new ArrayList<>();
            for (String part : name.getParts()) {
                parts.add(formatIdentifier(part));
            }
            return Joiner.on('.').join(parts);
        }

        @Override
        public String visitFieldReference(FieldReference node, StackableAstVisitorContext<Integer> indent)
        {
            // add colon so this won't parse
            return ":input(" + node.getFieldIndex() + ")";
        }

        @Override
        protected String visitFunctionCall(FunctionCall node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder();

            String arguments = joinExpressions(node.getArguments(), indent);
            if (node.getArguments().isEmpty() && "count".equalsIgnoreCase(node.getName().getSuffix())) {
                arguments = "*";
            }
            if (node.isDistinct()) {
                arguments = "DISTINCT " + arguments;
            }

            builder.append(formatQualifiedName(node.getName()))
                    .append('(').append(arguments).append(')');

            if (node.getFilter().isPresent()) {
                builder.append(" FILTER ").append(visitFilter(node.getFilter().get(), indent));
            }

            if (node.getWindow().isPresent()) {
                builder.append(" OVER ").append(visitWindow(node.getWindow().get(), indent));
            }

            return builder.toString();
        }

        @Override
        protected String visitLambdaExpression(LambdaExpression node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder();

            builder.append('(');
            Joiner.on(", ").appendTo(builder, node.getArguments());
            builder.append(") -> ");
            builder.append(process(node.getBody(), indent));
            return builder.toString();
        }

        @Override
        protected String visitLogicalBinaryExpression(LogicalBinaryExpression node, StackableAstVisitorContext<Integer> indent)
        {
            boolean sameTypeAsPreviousExpression = indent.getPreviousNode()
                    .map(previous -> previous instanceof LogicalBinaryExpression && ((LogicalBinaryExpression) previous).getType() == node.getType())
                    .orElse(false);

            if (!sameTypeAsPreviousExpression) {
                indent = increase(indent);
            }

            String formattedNode = process(node.getLeft(), indent) + '\n'
                    + indentString(indent.getContext() + 1) + node.getType().toString() + ' ' + process(node.getRight(), indent);
            if (sameTypeAsPreviousExpression) {
                return formattedNode;
            }
            else {
                return "(" + formattedNode + ")";
            }
        }

        private StackableAstVisitorContext<Integer> increase(StackableAstVisitorContext<Integer> indent)
        {
            return new StackableAstVisitorContext<>(indent.getContext() + 1, indent.getStack());
        }

        @Override
        protected String visitNotExpression(NotExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return "(NOT " + process(node.getValue(), indent) + ")";
        }

        @Override
        protected String visitComparisonExpression(ComparisonExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return formatBinaryExpression(node.getType().getValue(), node.getLeft(), node.getRight(), indent);
        }

        @Override
        protected String visitIsNullPredicate(IsNullPredicate node, StackableAstVisitorContext<Integer> indent)
        {
            return "(" + process(node.getValue(), indent) + " IS NULL)";
        }

        @Override
        protected String visitIsNotNullPredicate(IsNotNullPredicate node, StackableAstVisitorContext<Integer> indent)
        {
            return "(" + process(node.getValue(), indent) + " IS NOT NULL)";
        }

        @Override
        protected String visitNullIfExpression(NullIfExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return "NULLIF(" + process(node.getFirst(), indent) + ", " + process(node.getSecond(), indent) + ')';
        }

        @Override
        protected String visitIfExpression(IfExpression node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder();
            builder.append("IF(")
                    .append(process(node.getCondition(), indent))
                    .append(", ")
                    .append(process(node.getTrueValue(), indent));
            if (node.getFalseValue().isPresent()) {
                builder.append(", ")
                        .append(process(node.getFalseValue().get(), indent));
            }
            builder.append(")");
            return builder.toString();
        }

        @Override
        protected String visitTryExpression(TryExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return "TRY(" + process(node.getInnerExpression(), indent) + ")";
        }

        @Override
        protected String visitCoalesceExpression(CoalesceExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return "COALESCE(" + joinExpressions(node.getOperands(), indent) + ")";
        }

        @Override
        protected String visitArithmeticUnary(ArithmeticUnaryExpression node, StackableAstVisitorContext<Integer> indent)
        {
            String value = process(node.getValue(), indent);

            switch (node.getSign()) {
                case MINUS:
                    // this is to avoid turning a sequence of "-" into a comment (i.e., "-- comment")
                    String separator = value.startsWith("-") ? " " : "";
                    return "-" + separator + value;
                case PLUS:
                    return "+" + value;
                default:
                    throw new UnsupportedOperationException("Unsupported sign: " + node.getSign());
            }
        }

        @Override
        protected String visitArithmeticBinary(ArithmeticBinaryExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return formatBinaryExpression(node.getType().getValue(), node.getLeft(), node.getRight(), indent);
        }

        @Override
        protected String visitLikePredicate(LikePredicate node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder();

            builder.append('(')
                    .append(process(node.getValue(), indent))
                    .append(" LIKE ")
                    .append(process(node.getPattern(), indent));

            if (node.getEscape() != null) {
                builder.append(" ESCAPE ")
                        .append(process(node.getEscape(), indent));
            }

            builder.append(')');

            return builder.toString();
        }

        @Override
        protected String visitAllColumns(AllColumns node, StackableAstVisitorContext<Integer> indent)
        {
            if (node.getPrefix().isPresent()) {
                return node.getPrefix().get() + ".*";
            }

            return "*";
        }

        @Override
        public String visitCast(Cast node, StackableAstVisitorContext<Integer> indent)
        {
            return (node.isSafe() ? "TRY_CAST" : "CAST") +
                    "(" + process(node.getExpression(), indent) + " AS " + node.getType() + ")";
        }

        @Override
        protected String visitSearchedCaseExpression(SearchedCaseExpression node, StackableAstVisitorContext<Integer> indent)
        {
            ImmutableList.Builder<String> parts = ImmutableList.builder();
            parts.add("CASE");
            for (WhenClause whenClause : node.getWhenClauses()) {
                parts.add(process(whenClause, indent));
            }

            node.getDefaultValue()
                    .ifPresent((value) -> parts.add("ELSE").add(process(value, indent)));

            parts.add("END");

            return "(" + Joiner.on(' ').join(parts.build()) + ")";
        }

        @Override
        protected String visitSimpleCaseExpression(SimpleCaseExpression node, StackableAstVisitorContext<Integer> indent)
        {
            ImmutableList.Builder<String> parts = ImmutableList.builder();

            parts.add("CASE")
                    .add(process(node.getOperand(), indent));

            for (WhenClause whenClause : node.getWhenClauses()) {
                parts.add(process(whenClause, indent));
            }

            node.getDefaultValue()
                    .ifPresent((value) -> parts.add("ELSE").add(process(value, indent)));

            parts.add("END");

            return "(" + Joiner.on(' ').join(parts.build()) + ")";
        }

        @Override
        protected String visitWhenClause(WhenClause node, StackableAstVisitorContext<Integer> indent)
        {
            return "WHEN " + process(node.getOperand(), indent) + " THEN " + process(node.getResult(), indent);
        }

        @Override
        protected String visitBetweenPredicate(BetweenPredicate node, StackableAstVisitorContext<Integer> indent)
        {
            return "(" + process(node.getValue(), indent) + " BETWEEN " +
                    process(node.getMin(), indent) + " AND " + process(node.getMax(), indent) + ")";
        }

        @Override
        protected String visitInPredicate(InPredicate node, StackableAstVisitorContext<Integer> indent)
        {
            return "(" + process(node.getValue(), indent) + " IN " + process(node.getValueList(), indent) + ")";
        }

        @Override
        protected String visitInListExpression(InListExpression node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder("(");
            boolean first = true;
            for (Expression expression : node.getValues()) {
                builder.append("\n")
                        .append(indentString(indent.getContext() + 1))
                        .append(first ? "  " : ", ")
                        .append(process(expression, increase(indent)));
                first = false;
            }
            return builder.append(")").toString();
        }

        private String visitFilter(Expression node, StackableAstVisitorContext<Integer> indent)
        {
            return "(WHERE " + process(node, indent) + ')';
        }

        @Override
        public String visitWindow(Window node, StackableAstVisitorContext<Integer> indent)
        {
            List<String> parts = new ArrayList<>();

            if (!node.getPartitionBy().isEmpty()) {
                parts.add("PARTITION BY " + joinExpressions(node.getPartitionBy(), indent));
            }
            if (node.getOrderBy().isPresent()) {
                parts.add("ORDER BY " + formatSortItems(node.getOrderBy().get().getSortItems(), parameters, indent.getContext()));
            }
            if (node.getFrame().isPresent()) {
                parts.add(process(node.getFrame().get(), indent));
            }

            return '(' + Joiner.on(' ').join(parts) + ')';
        }

        @Override
        public String visitWindowFrame(WindowFrame node, StackableAstVisitorContext<Integer> indent)
        {
            StringBuilder builder = new StringBuilder();

            builder.append(node.getType().toString()).append(' ');

            if (node.getEnd().isPresent()) {
                builder.append("BETWEEN ")
                        .append(process(node.getStart(), indent))
                        .append(" AND ")
                        .append(process(node.getEnd().get(), indent));
            }
            else {
                builder.append(process(node.getStart(), indent));
            }

            return builder.toString();
        }

        @Override
        public String visitFrameBound(FrameBound node, StackableAstVisitorContext<Integer> indent)
        {
            switch (node.getType()) {
                case UNBOUNDED_PRECEDING:
                    return "UNBOUNDED PRECEDING";
                case PRECEDING:
                    return process(node.getValue().get(), indent) + " PRECEDING";
                case CURRENT_ROW:
                    return "CURRENT ROW";
                case FOLLOWING:
                    return process(node.getValue().get(), indent) + " FOLLOWING";
                case UNBOUNDED_FOLLOWING:
                    return "UNBOUNDED FOLLOWING";
            }
            throw new IllegalArgumentException("unhandled type: " + node.getType());
        }

        @Override
        protected String visitQuantifiedComparisonExpression(QuantifiedComparisonExpression node, StackableAstVisitorContext<Integer> indent)
        {
            return new StringBuilder()
                    .append("(")
                    .append(process(node.getValue(), indent))
                    .append(' ')
                    .append(node.getComparisonType().getValue())
                    .append(' ')
                    .append(node.getQuantifier().toString())
                    .append(' ')
                    .append(process(node.getSubquery(), indent))
                    .append(")")
                    .toString();
        }

        private String formatBinaryExpression(String operator, Expression left, Expression right, StackableAstVisitorContext<Integer> indent)
        {
            return "(" + process(left, increase(indent)) + ' ' + operator + ' ' + process(right, increase(indent)) + ')';
        }

        private String joinExpressions(List<Expression> expressions, StackableAstVisitorContext<Integer> indent)
        {
            return Joiner.on(", ").join(expressions.stream()
                    .map((e) -> process(e, indent))
                    .iterator());
        }

        private static String formatIdentifier(String s)
        {
            // TODO: handle escaping properly
            return '"' + s + '"';
        }
    }

    static String formatStringLiteral(String s)
    {
        return "'" + s.replace("'", "''") + "'";
    }

    static String formatSortItems(List<SortItem> sortItems, Optional<List<Expression>> parameters, int indent)
    {
        return Joiner.on(", ").join(sortItems.stream()
                .map(sortItemFormatterFunction(parameters, indent))
                .iterator());
    }

    static String formatGroupBy(List<GroupingElement> groupingElements, int indent)
    {
        return formatGroupBy(groupingElements, Optional.empty(), indent);
    }

    static String formatGroupBy(List<GroupingElement> groupingElements, Optional<List<Expression>> parameters, int indent)
    {
        ImmutableList.Builder<String> resultStrings = ImmutableList.builder();

        for (GroupingElement groupingElement : groupingElements) {
            String result = "";
            if (groupingElement instanceof SimpleGroupBy) {
                Set<Expression> columns = ImmutableSet.copyOf(((SimpleGroupBy) groupingElement).getColumnExpressions());
                if (columns.size() == 1) {
                    result = formatExpression(getOnlyElement(columns), parameters, indent);
                }
                else {
                    result = formatGroupingSet(columns, parameters, indent);
                }
            }
            else if (groupingElement instanceof GroupingSets) {
                result = format("GROUPING SETS (%s)", Joiner.on(", ").join(
                        ((GroupingSets) groupingElement).getSets().stream()
                                .map(ExpressionFormatter::formatGroupingSet)
                                .iterator()));
            }
            else if (groupingElement instanceof Cube) {
                result = format("CUBE %s", formatGroupingSet(((Cube) groupingElement).getColumns()));
            }
            else if (groupingElement instanceof Rollup) {
                result = format("ROLLUP %s", formatGroupingSet(((Rollup) groupingElement).getColumns()));
            }
            resultStrings.add(result);
        }
        return Joiner.on(", ").join(resultStrings.build());
    }

    private static String formatGroupingSet(List<QualifiedName> groupingSet)
    {
        return format("(%s)", Joiner.on(", ").join(groupingSet));
    }

    private static String formatGroupingSet(Set<Expression> groupingSet, Optional<List<Expression>> parameters, int indent)
    {
        return format("(%s)", Joiner.on(", ").join(groupingSet.stream()
                .map(e -> formatExpression(e, parameters, indent))
                .iterator()));
    }

    private static Function<SortItem, String> sortItemFormatterFunction(Optional<List<Expression>> parameters, int indent)
    {
        return input -> {
            StringBuilder builder = new StringBuilder();

            builder.append(formatExpression(input.getSortKey(), parameters, indent));

            switch (input.getOrdering()) {
                case ASCENDING:
                    builder.append(" ASC");
                    break;
                case DESCENDING:
                    builder.append(" DESC");
                    break;
                default:
                    throw new UnsupportedOperationException("unknown ordering: " + input.getOrdering());
            }

            switch (input.getNullOrdering()) {
                case FIRST:
                    builder.append(" NULLS FIRST");
                    break;
                case LAST:
                    builder.append(" NULLS LAST");
                    break;
                case UNDEFINED:
                    // no op
                    break;
                default:
                    throw new UnsupportedOperationException("unknown null ordering: " + input.getNullOrdering());
            }

            return builder.toString();
        };
    }
}

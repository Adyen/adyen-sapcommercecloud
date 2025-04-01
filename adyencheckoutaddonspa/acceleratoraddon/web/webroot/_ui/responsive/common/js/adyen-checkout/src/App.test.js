import { render, screen } from '@testing-library/react';
import CheckoutStepWrapper from './CheckoutStepWrapper';

test('renders learn react link', () => {
  render(<CheckoutStepWrapper />);
  const linkElement = screen.getByText(/learn react/i);
  expect(linkElement).toBeInTheDocument();
});
